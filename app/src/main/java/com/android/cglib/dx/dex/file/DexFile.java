
package com.android.cglib.dx.dex.file;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Adler32;

import com.android.cglib.dx.dex.DexOptions;
import com.android.cglib.dx.dex.file.MixedItemSection.SortType;
import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstBaseMethodRef;
import com.android.cglib.dx.rop.cst.CstEnumRef;
import com.android.cglib.dx.rop.cst.CstFieldRef;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.rop.type.Type;
import com.android.cglib.dx.util.ByteArrayAnnotatedOutput;
import com.android.cglib.dx.util.ExceptionWithContext;

public final class DexFile {
    private DexOptions dexOptions;

    private final MixedItemSection wordData;

    private final MixedItemSection typeLists;

    private final MixedItemSection map;

    private final MixedItemSection stringData;

    private final StringIdsSection stringIds;

    private final TypeIdsSection typeIds;

    private final ProtoIdsSection protoIds;

    private final FieldIdsSection fieldIds;

    private final MethodIdsSection methodIds;

    private final ClassDefsSection classDefs;

    private final MixedItemSection classData;

    private final MixedItemSection byteData;

    private final HeaderSection header;

    private final Section[] sections;

    private int fileSize;

    private int dumpWidth;

    public DexFile(DexOptions dexOptions) {
        this.dexOptions = dexOptions;

        header = new HeaderSection(this);
        typeLists = new MixedItemSection(null, this, 4, SortType.NONE);
        wordData = new MixedItemSection("word_data", this, 4, SortType.TYPE);
        stringData =
            new MixedItemSection("string_data", this, 1, SortType.INSTANCE);
        classData = new MixedItemSection(null, this, 1, SortType.NONE);
        byteData = new MixedItemSection("byte_data", this, 1, SortType.TYPE);
        stringIds = new StringIdsSection(this);
        typeIds = new TypeIdsSection(this);
        protoIds = new ProtoIdsSection(this);
        fieldIds = new FieldIdsSection(this);
        methodIds = new MethodIdsSection(this);
        classDefs = new ClassDefsSection(this);
        map = new MixedItemSection("map", this, 4, SortType.NONE);

        sections = new Section[] {
            header, stringIds, typeIds, protoIds, fieldIds, methodIds,
            classDefs, wordData, typeLists, stringData, byteData,
            classData, map };

        fileSize = -1;
        dumpWidth = 79;
    }

    public boolean isEmpty() {
        return classDefs.items().isEmpty();
    }

    public DexOptions getDexOptions() {
        return dexOptions;
    }

    public void add(ClassDefItem clazz) {
        classDefs.add(clazz);
    }

    public ClassDefItem getClassOrNull(String name) {
        try {
            Type type = Type.internClassName(name);
            return (ClassDefItem) classDefs.get(new CstType(type));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void writeTo(OutputStream out, Writer humanOut, boolean verbose)
        throws IOException {
        boolean annotate = (humanOut != null);
        ByteArrayAnnotatedOutput result = toDex0(annotate, verbose);

        if (out != null) {
            out.write(result.getArray());
        }

        if (annotate) {
            result.writeAnnotationsTo(humanOut);
        }
    }

    public byte[] toDex(Writer humanOut, boolean verbose)
        throws IOException {
        boolean annotate = (humanOut != null);
        ByteArrayAnnotatedOutput result = toDex0(annotate, verbose);

        if (annotate) {
            result.writeAnnotationsTo(humanOut);
        }

        return result.getArray();
    }

    public void setDumpWidth(int dumpWidth) {
        if (dumpWidth < 40) {
            throw new IllegalArgumentException("dumpWidth < 40");
        }

        this.dumpWidth = dumpWidth;
    }

     int getFileSize() {
        if (fileSize < 0) {
            throw new RuntimeException("file size not yet known");
        }

        return fileSize;
    }

     MixedItemSection getStringData() {
        return stringData;
    }

     MixedItemSection getWordData() {
        return wordData;
    }

     MixedItemSection getTypeLists() {
        return typeLists;
    }

     MixedItemSection getMap() {
        return map;
    }

     StringIdsSection getStringIds() {
        return stringIds;
    }

     ClassDefsSection getClassDefs() {
        return classDefs;
    }

     MixedItemSection getClassData() {
        return classData;
    }

     TypeIdsSection getTypeIds() {
        return typeIds;
    }

     ProtoIdsSection getProtoIds() {
        return protoIds;
    }

     FieldIdsSection getFieldIds() {
        return fieldIds;
    }

     MethodIdsSection getMethodIds() {
        return methodIds;
    }

     MixedItemSection getByteData() {
        return byteData;
    }

     Section getFirstDataSection() {
        return wordData;
    }

     Section getLastDataSection() {
        return map;
    }

     void internIfAppropriate(Constant cst) {
        if (cst instanceof CstString) {
            stringIds.intern((CstString) cst);
        } else if (cst instanceof CstType) {
            typeIds.intern((CstType) cst);
        } else if (cst instanceof CstBaseMethodRef) {
            methodIds.intern((CstBaseMethodRef) cst);
        } else if (cst instanceof CstFieldRef) {
            fieldIds.intern((CstFieldRef) cst);
        } else if (cst instanceof CstEnumRef) {
            fieldIds.intern(((CstEnumRef) cst).getFieldRef());
        } else if (cst == null) {
            throw new NullPointerException("cst == null");
        }
    }

     IndexedItem findItemOrNull(Constant cst) {
        IndexedItem item;

        if (cst instanceof CstString) {
            return stringIds.get(cst);
        } else if (cst instanceof CstType) {
            return typeIds.get(cst);
        } else if (cst instanceof CstBaseMethodRef) {
            return methodIds.get(cst);
        } else if (cst instanceof CstFieldRef) {
            return fieldIds.get(cst);
        } else {
            return null;
        }
    }

    private ByteArrayAnnotatedOutput toDex0(boolean annotate,
            boolean verbose) {

        classDefs.prepare();
        classData.prepare();
        wordData.prepare();
        byteData.prepare();
        methodIds.prepare();
        fieldIds.prepare();
        protoIds.prepare();
        typeLists.prepare();
        typeIds.prepare();
        stringIds.prepare();
        stringData.prepare();
        header.prepare();


        int count = sections.length;
        int offset = 0;

        for (int i = 0; i < count; i++) {
            Section one = sections[i];
            int placedAt = one.setFileOffset(offset);
            if (placedAt < offset) {
                throw new RuntimeException("bogus placement for section " + i);
            }

            try {
                if (one == map) {
                    MapItem.addMap(sections, map);
                    map.prepare();
                }

                if (one instanceof MixedItemSection) {
                    ((MixedItemSection) one).placeItems();
                }

                offset = placedAt + one.writeSize();
            } catch (RuntimeException ex) {
                throw ExceptionWithContext.withContext(ex,
                        "...while writing section " + i);
            }
        }


        fileSize = offset;
        byte[] barr = new byte[fileSize];
        ByteArrayAnnotatedOutput out = new ByteArrayAnnotatedOutput(barr);

        if (annotate) {
            out.enableAnnotations(dumpWidth, verbose);
        }

        for (int i = 0; i < count; i++) {
            try {
                Section one = sections[i];
                int zeroCount = one.getFileOffset() - out.getCursor();
                if (zeroCount < 0) {
                    throw new ExceptionWithContext("excess write of " +
                            (-zeroCount));
                }
                out.writeZeroes(one.getFileOffset() - out.getCursor());
                one.writeTo(out);
            } catch (RuntimeException ex) {
                ExceptionWithContext ec;
                if (ex instanceof ExceptionWithContext) {
                    ec = (ExceptionWithContext) ex;
                } else {
                    ec = new ExceptionWithContext(ex);
                }
                ec.addContext("...while writing section " + i);
                throw ec;
            }
        }

        if (out.getCursor() != fileSize) {
            throw new RuntimeException("foreshortened write");
        }


        calcSignature(barr);
        calcChecksum(barr);

        if (annotate) {
            wordData.writeIndexAnnotation(out, ItemType.TYPE_CODE_ITEM,
                    "\nmethod code index:\n\n");
            getStatistics().writeAnnotation(out);
            out.finishAnnotating();
        }

        return out;
    }

    public Statistics getStatistics() {
        Statistics stats = new Statistics();

        for (Section s : sections) {
            stats.addAll(s);
        }

        return stats;
    }

    private static void calcSignature(byte[] bytes) {
        MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

        md.update(bytes, 32, bytes.length - 32);

        try {
            int amt = md.digest(bytes, 12, 20);
            if (amt != 20) {
                throw new RuntimeException("unexpected digest write: " + amt +
                                           " bytes");
            }
        } catch (DigestException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void calcChecksum(byte[] bytes) {
        Adler32 a32 = new Adler32();

        a32.update(bytes, 12, bytes.length - 12);

        int sum = (int) a32.getValue();

        bytes[8]  = (byte) sum;
        bytes[9]  = (byte) (sum >> 8);
        bytes[10] = (byte) (sum >> 16);
        bytes[11] = (byte) (sum >> 24);
    }
}
