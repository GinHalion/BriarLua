
package com.android.cglib.dx;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.STATIC;
import static com.android.cglib.dx.rop.code.AccessFlags.ACC_CONSTRUCTOR;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.android.cglib.dx.dex.DexFormat;
import com.android.cglib.dx.dex.DexOptions;
import com.android.cglib.dx.dex.code.DalvCode;
import com.android.cglib.dx.dex.code.PositionList;
import com.android.cglib.dx.dex.code.RopTranslator;
import com.android.cglib.dx.dex.file.ClassDefItem;
import com.android.cglib.dx.dex.file.DexFile;
import com.android.cglib.dx.dex.file.EncodedField;
import com.android.cglib.dx.dex.file.EncodedMethod;
import com.android.cglib.dx.rop.code.AccessFlags;
import com.android.cglib.dx.rop.code.LocalVariableInfo;
import com.android.cglib.dx.rop.code.RopMethod;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.rop.type.StdTypeList;

public final class DexMaker {
    private final Map<TypeId<?>, TypeDeclaration> types
            = new LinkedHashMap<TypeId<?>, TypeDeclaration>();

    public DexMaker() {
    }

    private TypeDeclaration getTypeDeclaration(TypeId<?> type) {
        TypeDeclaration result = types.get(type);
        if (result == null) {
            result = new TypeDeclaration(type);
            types.put(type, result);
        }
        return result;
    }

    public void declare(TypeId<?> type, String sourceFile, int flags,
            TypeId<?> supertype, TypeId<?>... interfaces) {
        TypeDeclaration declaration = getTypeDeclaration(type);
        int supportedFlags = Modifier.PUBLIC | Modifier.FINAL | Modifier.ABSTRACT;
        if ((flags & ~supportedFlags) != 0) {
            throw new IllegalArgumentException("Unexpected flag: "
                    + Integer.toHexString(flags));
        }
        if (declaration.declared) {
            throw new IllegalStateException("already declared: " + type);
        }
        declaration.declared = true;
        declaration.flags = flags;
        declaration.supertype = supertype;
        declaration.sourceFile = sourceFile;
        declaration.interfaces = new TypeList(interfaces);
    }

    public Code declare(MethodId<?, ?> method, int flags) {
        TypeDeclaration typeDeclaration = getTypeDeclaration(method.declaringType);
        if (typeDeclaration.methods.containsKey(method)) {
            throw new IllegalStateException("already declared: " + method);
        }

        int supportedFlags = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED
                | Modifier.STATIC | Modifier.FINAL | Modifier.SYNCHRONIZED;
        if ((flags & ~supportedFlags) != 0) {
            throw new IllegalArgumentException("Unexpected flag: "
                    + Integer.toHexString(flags));
        }

        if ((flags & Modifier.SYNCHRONIZED) != 0) {
            flags = (flags & ~Modifier.SYNCHRONIZED) | AccessFlags.ACC_DECLARED_SYNCHRONIZED;
        }

        if (method.isConstructor()) {
            flags |= ACC_CONSTRUCTOR;
        }

        MethodDeclaration methodDeclaration = new MethodDeclaration(method, flags);
        typeDeclaration.methods.put(method, methodDeclaration);
        return methodDeclaration.code;
    }

    public void declare(FieldId<?, ?> fieldId, int flags, Object staticValue) {
        TypeDeclaration typeDeclaration = getTypeDeclaration(fieldId.declaringType);
        if (typeDeclaration.fields.containsKey(fieldId)) {
            throw new IllegalStateException("already declared: " + fieldId);
        }

        int supportedFlags = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED
                | Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE | Modifier.TRANSIENT;
        if ((flags & ~supportedFlags) != 0) {
            throw new IllegalArgumentException("Unexpected flag: "
                    + Integer.toHexString(flags));
        }

        if ((flags & Modifier.STATIC) == 0 && staticValue != null) {
            throw new IllegalArgumentException("staticValue is non-null, but field is not static");
        }

        FieldDeclaration fieldDeclaration = new FieldDeclaration(fieldId, flags, staticValue);
        typeDeclaration.fields.put(fieldId, fieldDeclaration);
    }

    public byte[] generate() {
        DexOptions options = new DexOptions();
        options.targetApiLevel = DexFormat.API_NO_EXTENDED_OPCODES;
        DexFile outputDex = new DexFile(options);

        for (TypeDeclaration typeDeclaration : types.values()) {
            outputDex.add(typeDeclaration.toClassDefItem());
        }

        try {
            return outputDex.toDex(null, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateFileName() {
        int checksum = 1;

        Set<TypeId<?>> typesKeySet = types.keySet();
        Iterator<TypeId<?>> it = typesKeySet.iterator();
        int[] checksums = new int[typesKeySet.size()];
        int i = 0;

        while (it.hasNext()) {
            TypeId<?> typeId = it.next();
            TypeDeclaration decl = getTypeDeclaration(typeId);
            Set<MethodId> methodSet = decl.methods.keySet();
            if (decl.supertype != null) {
                checksums[i++] = 31 * decl.supertype.hashCode() + methodSet.hashCode();
            }
        }
        Arrays.sort(checksums);

        for (int sum : checksums) {
            checksum *= 31;
            checksum += sum;
        }

        return "Generated_" + checksum +".jar";
    }

    private ClassLoader generateClassLoader(File result, File dexCache, ClassLoader parent) {
        try {
            return (ClassLoader) Class.forName("dalvik.system.DexClassLoader")
                    .getConstructor(String.class, String.class, String.class, ClassLoader.class)
                    .newInstance(result.getPath(), dexCache.getAbsolutePath(), null, parent);
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("load() requires a Dalvik VM", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (InstantiationException e) {
            throw new AssertionError();
        } catch (NoSuchMethodException e) {
            throw new AssertionError();
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        }
    }

    public ClassLoader generateAndLoad(ClassLoader parent, File dexCache) throws IOException {
        if (dexCache == null) {
            String property = System.getProperty("dexmaker.dexcache");
            if (property != null) {
                dexCache = new File(property);
            } else {
                dexCache = new AppDataDirGuesser().guess();
                if (dexCache == null) {
                    throw new IllegalArgumentException("dexcache == null (and no default could be"
                            + " found; consider setting the 'dexmaker.dexcache' system property)");
                }
            }
        }

        File result = new File(dexCache, generateFileName());
        if (result.exists()) {
            ensureReadOnly(result);
            return generateClassLoader(result, dexCache, parent);
        }

        byte[] dex = generate();

        result.createNewFile();
        JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(result));
        JarEntry entry = new JarEntry(DexFormat.DEX_IN_JAR_NAME);
        entry.setSize(dex.length);
        jarOut.putNextEntry(entry);
        jarOut.write(dex);
        jarOut.closeEntry();
        jarOut.close();
        ensureReadOnly(result);
        return generateClassLoader(result, dexCache, parent);
    }

    private static void ensureReadOnly(File file) {
        if (!file.exists())
            return;
        try {
            if (!file.setWritable(false, false) && !file.setReadOnly()) {
                if (file.canWrite()) {
                    System.err.println("DexMaker: cannot make " + file + " read-only");
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    private static class TypeDeclaration {
        private final TypeId<?> type;

        private boolean declared;
        private int flags;
        private TypeId<?> supertype;
        private String sourceFile;
        private TypeList interfaces;

        private final Map<FieldId, FieldDeclaration> fields
                = new LinkedHashMap<FieldId, FieldDeclaration>();
        private final Map<MethodId, MethodDeclaration> methods
                = new LinkedHashMap<MethodId, MethodDeclaration>();

        TypeDeclaration(TypeId<?> type) {
            this.type = type;
        }

        ClassDefItem toClassDefItem() {
            if (!declared) {
                throw new IllegalStateException("Undeclared type " + type + " declares members: "
                        + fields.keySet() + " " + methods.keySet());
            }

            DexOptions dexOptions = new DexOptions();
            dexOptions.targetApiLevel = DexFormat.API_NO_EXTENDED_OPCODES;

            CstType thisType = type.constant;

            ClassDefItem out = new ClassDefItem(thisType, flags, supertype.constant,
                    interfaces.ropTypes, new CstString(sourceFile));

            for (MethodDeclaration method : methods.values()) {
                EncodedMethod encoded = method.toEncodedMethod(dexOptions);
                if (method.isDirect()) {
                    out.addDirectMethod(encoded);
                } else {
                    out.addVirtualMethod(encoded);
                }
            }
            for (FieldDeclaration field : fields.values()) {
                EncodedField encoded = field.toEncodedField();
                if (field.isStatic()) {
                    out.addStaticField(encoded, Constants.getConstant(field.staticValue));
                } else {
                    out.addInstanceField(encoded);
                }
            }

            return out;
        }
    }

    static class FieldDeclaration {
        final FieldId<?, ?> fieldId;
        private final int accessFlags;
        private final Object staticValue;

        FieldDeclaration(FieldId<?, ?> fieldId, int accessFlags, Object staticValue) {
            if ((accessFlags & STATIC) == 0 && staticValue != null) {
                throw new IllegalArgumentException("instance fields may not have a value");
            }
            this.fieldId = fieldId;
            this.accessFlags = accessFlags;
            this.staticValue = staticValue;
        }

        EncodedField toEncodedField() {
            return new EncodedField(fieldId.constant, accessFlags);
        }

        public boolean isStatic() {
            return (accessFlags & STATIC) != 0;
        }
    }

    static class MethodDeclaration {
        final MethodId<?, ?> method;
        private final int flags;
        private final Code code;

        public MethodDeclaration(MethodId<?, ?> method, int flags) {
            this.method = method;
            this.flags = flags;
            this.code = new Code(this);
        }

        boolean isStatic() {
            return (flags & STATIC) != 0;
        }

        boolean isDirect() {
            return (flags & (STATIC | PRIVATE | ACC_CONSTRUCTOR)) != 0;
        }

        EncodedMethod toEncodedMethod(DexOptions dexOptions) {
            RopMethod ropMethod = new RopMethod(code.toBasicBlocks(), 0);
            LocalVariableInfo locals = null;
            DalvCode dalvCode = RopTranslator.translate(
                    ropMethod, PositionList.NONE, locals, code.paramSize(), dexOptions);
            return new EncodedMethod(method.constant, flags, dalvCode, StdTypeList.EMPTY);
        }
    }
}
