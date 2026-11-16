
package com.android.cglib.dx.stock;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.android.cglib.dx.Code;
import com.android.cglib.dx.Comparison;
import com.android.cglib.dx.DexMaker;
import com.android.cglib.dx.FieldId;
import com.android.cglib.dx.Label;
import com.android.cglib.dx.Local;
import com.android.cglib.dx.MethodId;
import com.android.cglib.dx.TypeId;

public final class ProxyBuilder<T> {
    public static final int VERSION = 1;

    private static final String FIELD_NAME_HANDLER = "$__handler";
    private static final String FIELD_NAME_METHODS = "$__methodArray";

    private static final Map<Class<?>, Class<?>> generatedProxyClasses
            = Collections.synchronizedMap(new HashMap<Class<?>, Class<?>>());

    private final Class<T> baseClass;
    private ClassLoader parentClassLoader = ProxyBuilder.class.getClassLoader();
    private InvocationHandler handler;
    private File dexCache;
    private Class<?>[] constructorArgTypes = new Class[0];
    private Object[] constructorArgValues = new Object[0];
    private Set<Class<?>> interfaces = new HashSet<Class<?>>();

    private ProxyBuilder(Class<T> clazz) {
        baseClass = clazz;
    }

    public static <T> ProxyBuilder<T> forClass(Class<T> clazz) {
        return new ProxyBuilder<T>(clazz);
    }

    public ProxyBuilder<T> parentClassLoader(ClassLoader parent) {
        parentClassLoader = parent;
        return this;
    }

    public ProxyBuilder<T> handler(InvocationHandler handler) {
        this.handler = handler;
        return this;
    }

    public ProxyBuilder<T> dexCache(File dexCacheParent) {
        dexCache = new File(dexCacheParent, "v" + Integer.toString(VERSION));
        dexCache.mkdir();
        return this;
    }
    
    public ProxyBuilder<T> implementing(Class<?>... interfaces) {
        for (Class<?> i : interfaces) {
            if (!i.isInterface()) {
                throw new IllegalArgumentException("Not an interface: " + i.getName());
            }
            this.interfaces.add(i);
        }
        return this;
    }

    public ProxyBuilder<T> constructorArgValues(Object... constructorArgValues) {
        this.constructorArgValues = constructorArgValues;
        return this;
    }

    public ProxyBuilder<T> constructorArgTypes(Class<?>... constructorArgTypes) {
        this.constructorArgTypes = constructorArgTypes;
        return this;
    }

    public T build() throws IOException {
        check(handler != null, "handler == null");
        check(constructorArgTypes.length == constructorArgValues.length,
                "constructorArgValues.length != constructorArgTypes.length");
        Class<? extends T> proxyClass = buildProxyClass();
        Constructor<? extends T> constructor;
        try {
            constructor = proxyClass.getConstructor(constructorArgTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No constructor for " + baseClass.getName()
                    + " with parameter types " + Arrays.toString(constructorArgTypes));
        }
        T result;
        try {
            result = constructor.newInstance(constructorArgValues);
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            throw launderCause(e);
        }
        setInvocationHandler(result, handler);
        return result;
    }


    public Class<? extends T> buildProxyClass() throws IOException {
        @SuppressWarnings("unchecked")
        Class<? extends T> proxyClass = (Class) generatedProxyClasses.get(baseClass);
        if (proxyClass != null
                && proxyClass.getClassLoader().getParent() == parentClassLoader
                && interfaces.equals(asSet(proxyClass.getInterfaces()))) {
            return proxyClass;
        }

        DexMaker dexMaker = new DexMaker();
        String generatedName = getMethodNameForProxyOf(baseClass);
        TypeId<? extends T> generatedType = TypeId.get("L" + generatedName + ";");
        TypeId<T> superType = TypeId.get(baseClass);
        generateConstructorsAndFields(dexMaker, generatedType, superType, baseClass);
        Method[] methodsToProxy = getMethodsToProxyRecursive();
        generateCodeForAllMethods(dexMaker, generatedType, methodsToProxy, superType);
        dexMaker.declare(generatedType, generatedName + ".generated", PUBLIC, superType,
                getInterfacesAsTypeIds());
        ClassLoader classLoader = dexMaker.generateAndLoad(parentClassLoader, dexCache);
        try {
            proxyClass = loadClass(classLoader, generatedName);
        } catch (IllegalAccessError e) {
            throw new UnsupportedOperationException(
                    "cannot proxy inaccessible class " + baseClass, e);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        setMethodsStaticField(proxyClass, methodsToProxy);
        generatedProxyClasses.put(baseClass, proxyClass);
        return proxyClass;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends T> loadClass(ClassLoader classLoader, String generatedName)
            throws ClassNotFoundException {
        return (Class<? extends T>) classLoader.loadClass(generatedName);
    }

    private static RuntimeException launderCause(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        throw new UndeclaredThrowableException(cause);
    }

    private static void setMethodsStaticField(Class<?> proxyClass, Method[] methodsToProxy) {
        try {
            Field methodArrayField = proxyClass.getDeclaredField(FIELD_NAME_METHODS);
            methodArrayField.setAccessible(true);
            methodArrayField.set(null, methodsToProxy);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static InvocationHandler getInvocationHandler(Object instance) {
        try {
            Field field = instance.getClass().getDeclaredField(FIELD_NAME_HANDLER);
            field.setAccessible(true);
            return (InvocationHandler) field.get(instance);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Not a valid proxy instance", e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static void setInvocationHandler(Object instance, InvocationHandler handler) {
        try {
            Field handlerField = instance.getClass().getDeclaredField(FIELD_NAME_HANDLER);
            handlerField.setAccessible(true);
            handlerField.set(instance, handler);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Not a valid proxy instance", e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    
    public static boolean isProxyClass(Class<?> c) {
        try {
            c.getDeclaredField(FIELD_NAME_HANDLER);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static <T, G extends T> void generateCodeForAllMethods(DexMaker dexMaker,
            TypeId<G> generatedType, Method[] methodsToProxy, TypeId<T> superclassType) {
        TypeId<InvocationHandler> handlerType = TypeId.get(InvocationHandler.class);
        TypeId<Method[]> methodArrayType = TypeId.get(Method[].class);
        FieldId<G, InvocationHandler> handlerField =
                generatedType.getField(handlerType, FIELD_NAME_HANDLER);
        FieldId<G, Method[]> allMethods =
                generatedType.getField(methodArrayType, FIELD_NAME_METHODS);
        TypeId<Method> methodType = TypeId.get(Method.class);
        TypeId<Object[]> objectArrayType = TypeId.get(Object[].class);
        MethodId<InvocationHandler, Object> methodInvoke = handlerType.getMethod(TypeId.OBJECT,
                "invoke", TypeId.OBJECT, methodType, objectArrayType);
        for (int m = 0; m < methodsToProxy.length; ++m) {
            Method method = methodsToProxy[m];
            String name = method.getName();
            Class<?>[] argClasses = method.getParameterTypes();
            TypeId<?>[] argTypes = new TypeId<?>[argClasses.length];
            for (int i = 0; i < argTypes.length; ++i) {
                argTypes[i] = TypeId.get(argClasses[i]);
            }
            Class<?> returnType = method.getReturnType();
            TypeId<?> resultType = TypeId.get(returnType);
            MethodId<T, ?> superMethod = superclassType.getMethod(resultType, name, argTypes);
            MethodId<?, ?> methodId = generatedType.getMethod(resultType, name, argTypes);
            Code code = dexMaker.declare(methodId, PUBLIC);
            Local<G> localThis = code.getThis(generatedType);
            Local<InvocationHandler> localHandler = code.newLocal(handlerType);
            Local<Object> invokeResult = code.newLocal(TypeId.OBJECT);
            Local<Integer> intValue = code.newLocal(TypeId.INT);
            Local<Object[]> args = code.newLocal(objectArrayType);
            Local<Integer> argsLength = code.newLocal(TypeId.INT);
            Local<Object> temp = code.newLocal(TypeId.OBJECT);
            Local<?> resultHolder = code.newLocal(resultType);
            Local<Method[]> methodArray = code.newLocal(methodArrayType);
            Local<Method> thisMethod = code.newLocal(methodType);
            Local<Integer> methodIndex = code.newLocal(TypeId.INT);
            Class<?> aBoxedClass = PRIMITIVE_TO_BOXED.get(returnType);
            Local<?> aBoxedResult = null;
            if (aBoxedClass != null) {
                aBoxedResult = code.newLocal(TypeId.get(aBoxedClass));
            }
            Local<?>[] superArgs2 = new Local<?>[argClasses.length];
            Local<?> superResult2 = code.newLocal(resultType);
            Local<InvocationHandler> nullHandler = code.newLocal(handlerType);

            code.loadConstant(methodIndex, m);
            code.sget(allMethods, methodArray);
            code.aget(thisMethod, methodArray, methodIndex);
            code.loadConstant(argsLength, argTypes.length);
            code.newArray(args, argsLength);
            code.iget(handlerField, localHandler, localThis);

            code.loadConstant(nullHandler, null);
            Label handlerNullCase = new Label();
            code.compare(Comparison.EQ, handlerNullCase, nullHandler, localHandler);

            for (int p = 0; p < argTypes.length; ++p) {
                code.loadConstant(intValue, p);
                Local<?> parameter = code.getParameter(p, argTypes[p]);
                Local<?> unboxedIfNecessary = boxIfRequired(code, parameter, temp);
                code.aput(args, intValue, unboxedIfNecessary);
            }
            code.invokeInterface(methodInvoke, invokeResult, localHandler,
                    localThis, thisMethod, args);
            generateCodeForReturnStatement(code, returnType, invokeResult, resultHolder,
                    aBoxedResult);

            code.mark(handlerNullCase);
            for (int i = 0; i < superArgs2.length; ++i) {
                superArgs2[i] = code.getParameter(i, argTypes[i]);
            }
            if (void.class.equals(returnType)) {
                code.invokeSuper(superMethod, null, localThis, superArgs2);
                code.returnVoid();
            } else {
                invokeSuper(superMethod, code, localThis, superArgs2, superResult2);
                code.returnValue(superResult2);
            }

            MethodId<G, ?> callsSuperMethod = generatedType.getMethod(
                    resultType, superMethodName(method), argTypes);
            Code superCode = dexMaker.declare(callsSuperMethod, PUBLIC);
            Local<G> superThis = superCode.getThis(generatedType);
            Local<?>[] superArgs = new Local<?>[argClasses.length];
            for (int i = 0; i < superArgs.length; ++i) {
                superArgs[i] = superCode.getParameter(i, argTypes[i]);
            }
            if (void.class.equals(returnType)) {
                superCode.invokeSuper(superMethod, null, superThis, superArgs);
                superCode.returnVoid();
            } else {
                Local<?> superResult = superCode.newLocal(resultType);
                invokeSuper(superMethod, superCode, superThis, superArgs, superResult);
                superCode.returnValue(superResult);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void invokeSuper(MethodId superMethod, Code superCode,
            Local superThis, Local[] superArgs, Local superResult) {
        superCode.invokeSuper(superMethod, superResult, superThis, superArgs);
    }

    private static Local<?> boxIfRequired(Code code, Local<?> parameter, Local<Object> temp) {
        MethodId<?, ?> unboxMethod = PRIMITIVE_TYPE_TO_UNBOX_METHOD.get(parameter.getType());
        if (unboxMethod == null) {
            return parameter;
        }
        code.invokeStatic(unboxMethod, temp, parameter);
        return temp;
    }

    public static Object callSuper(Object proxy, Method method, Object... args) throws Throwable {
        try {
            return proxy.getClass()
                    .getMethod(superMethodName(method), method.getParameterTypes())
                    .invoke(proxy, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static String superMethodName(Method method) {
        String returnType = method.getReturnType().getName();
        return "super$" + method.getName() + "$"
                + returnType.replace('.', '_').replace('[', '_').replace(';', '_');
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T, G extends T> void generateConstructorsAndFields(DexMaker dexMaker,
            TypeId<G> generatedType, TypeId<T> superType, Class<T> superClass) {
        TypeId<InvocationHandler> handlerType = TypeId.get(InvocationHandler.class);
        TypeId<Method[]> methodArrayType = TypeId.get(Method[].class);
        FieldId<G, InvocationHandler> handlerField = generatedType.getField(
                handlerType, FIELD_NAME_HANDLER);
        dexMaker.declare(handlerField, PRIVATE, null);
        FieldId<G, Method[]> allMethods = generatedType.getField(
                methodArrayType, FIELD_NAME_METHODS);
        dexMaker.declare(allMethods, PRIVATE | STATIC, null);
        for (Constructor<T> constructor : getConstructorsToOverwrite(superClass)) {
            if (constructor.getModifiers() == Modifier.FINAL) {
                continue;
            }
            TypeId<?>[] types = classArrayToTypeArray(constructor.getParameterTypes());
            MethodId<?, ?> method = generatedType.getConstructor(types);
            Code constructorCode = dexMaker.declare(method, PUBLIC);
            Local<G> thisRef = constructorCode.getThis(generatedType);
            Local<?>[] params = new Local[types.length];
            for (int i = 0; i < params.length; ++i) {
                params[i] = constructorCode.getParameter(i, types[i]);
            }
            MethodId<T, ?> superConstructor = superType.getConstructor(types);
            constructorCode.invokeDirect(superConstructor, null, thisRef, params);
            constructorCode.returnVoid();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T>[] getConstructorsToOverwrite(Class<T> clazz) {
        return (Constructor<T>[]) clazz.getDeclaredConstructors();
    }

    private TypeId<?>[] getInterfacesAsTypeIds() {
        TypeId<?>[] result = new TypeId<?>[interfaces.size()];
        int i = 0;
        for (Class<?> implemented : interfaces) {
            result[i++] = TypeId.get(implemented);
        }
        return result;
    }

    private Method[] getMethodsToProxyRecursive() {
        Set<MethodSetEntry> methodsToProxy = new HashSet<MethodSetEntry>();
        Set<MethodSetEntry> seenFinalMethods = new HashSet<MethodSetEntry>();
        for (Class<?> c = baseClass; c != null; c = c.getSuperclass()) {
            getMethodsToProxy(methodsToProxy, seenFinalMethods, c);
        }
        for (Class<?> c = baseClass; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                getMethodsToProxy(methodsToProxy, seenFinalMethods, i);
            }
        }
        for (Class<?> c : interfaces) {
            getMethodsToProxy(methodsToProxy, seenFinalMethods, c);
        }

        Method[] results = new Method[methodsToProxy.size()];
        int i = 0;
        for (MethodSetEntry entry : methodsToProxy) {
            results[i++] = entry.originalMethod;
        }

        Arrays.sort(results, new Comparator<Method>() {
            @Override
            public int compare(Method method1, Method method2) {
                return method1.toString().compareTo(method2.toString());
            }
        });

        return results;
    }

    private void getMethodsToProxy(Set<MethodSetEntry> sink, Set<MethodSetEntry> seenFinalMethods,
            Class<?> c) {
        for (Method method : c.getDeclaredMethods()) {
            if ((method.getModifiers() & Modifier.FINAL) != 0) {
                MethodSetEntry entry = new MethodSetEntry(method);
                seenFinalMethods.add(entry);
                sink.remove(entry);
                continue;
            }
            if ((method.getModifiers() & STATIC) != 0) {
                continue;
            }
            if (!Modifier.isPublic(method.getModifiers())
					&& !Modifier.isProtected(method.getModifiers())) {

                continue;
            }
            if (method.getName().equals("finalize") && method.getParameterTypes().length == 0) {
                continue;
            }
            MethodSetEntry entry = new MethodSetEntry(method);
            if (seenFinalMethods.contains(entry)) {
                continue;
            }
            sink.add(entry);
        }
    }

    private static <T> String getMethodNameForProxyOf(Class<T> clazz) {
        return clazz.getSimpleName() + "_Proxy";
    }

    private static TypeId<?>[] classArrayToTypeArray(Class<?>[] input) {
        TypeId<?>[] result = new TypeId[input.length];
        for (int i = 0; i < input.length; ++i) {
            result[i] = TypeId.get(input[i]);
        }
        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void generateCodeForReturnStatement(Code code, Class methodReturnType,
            Local localForResultOfInvoke, Local localOfMethodReturnType, Local aBoxedResult) {
        if (PRIMITIVE_TO_UNBOX_METHOD.containsKey(methodReturnType)) {
            code.cast(aBoxedResult, localForResultOfInvoke);
            MethodId unboxingMethodFor = getUnboxMethodForPrimitive(methodReturnType);
            code.invokeVirtual(unboxingMethodFor, localOfMethodReturnType, aBoxedResult);
            code.returnValue(localOfMethodReturnType);
        } else if (void.class.equals(methodReturnType)) {
            code.returnVoid();
        } else {
            code.cast(localOfMethodReturnType, localForResultOfInvoke);
            code.returnValue(localOfMethodReturnType);
        }
    }

    private static <T> Set<T> asSet(T... array) {
        return new CopyOnWriteArraySet<T>(Arrays.asList(array));
    }

    private static MethodId<?, ?> getUnboxMethodForPrimitive(Class<?> methodReturnType) {
        return PRIMITIVE_TO_UNBOX_METHOD.get(methodReturnType);
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXED;
    static {
        PRIMITIVE_TO_BOXED = new HashMap<Class<?>, Class<?>>();
        PRIMITIVE_TO_BOXED.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_BOXED.put(int.class, Integer.class);
        PRIMITIVE_TO_BOXED.put(byte.class, Byte.class);
        PRIMITIVE_TO_BOXED.put(long.class, Long.class);
        PRIMITIVE_TO_BOXED.put(short.class, Short.class);
        PRIMITIVE_TO_BOXED.put(float.class, Float.class);
        PRIMITIVE_TO_BOXED.put(double.class, Double.class);
        PRIMITIVE_TO_BOXED.put(char.class, Character.class);
    }

    private static final Map<TypeId<?>, MethodId<?, ?>> PRIMITIVE_TYPE_TO_UNBOX_METHOD;
    static {
        PRIMITIVE_TYPE_TO_UNBOX_METHOD = new HashMap<TypeId<?>, MethodId<?, ?>>();
        for (Map.Entry<Class<?>, Class<?>> entry : PRIMITIVE_TO_BOXED.entrySet()) {
            TypeId<?> primitiveType = TypeId.get(entry.getKey());
            TypeId<?> boxedType = TypeId.get(entry.getValue());
            MethodId<?, ?> valueOfMethod = boxedType.getMethod(boxedType, "valueOf", primitiveType);
            PRIMITIVE_TYPE_TO_UNBOX_METHOD.put(primitiveType, valueOfMethod);
        }
    }

    private static final Map<Class<?>, MethodId<?, ?>> PRIMITIVE_TO_UNBOX_METHOD;
    static {
        Map<Class<?>, MethodId<?, ?>> map = new HashMap<Class<?>, MethodId<?, ?>>();
        map.put(boolean.class, TypeId.get(Boolean.class).getMethod(TypeId.BOOLEAN, "booleanValue"));
        map.put(int.class, TypeId.get(Integer.class).getMethod(TypeId.INT, "intValue"));
        map.put(byte.class, TypeId.get(Byte.class).getMethod(TypeId.BYTE, "byteValue"));
        map.put(long.class, TypeId.get(Long.class).getMethod(TypeId.LONG, "longValue"));
        map.put(short.class, TypeId.get(Short.class).getMethod(TypeId.SHORT, "shortValue"));
        map.put(float.class, TypeId.get(Float.class).getMethod(TypeId.FLOAT, "floatValue"));
        map.put(double.class, TypeId.get(Double.class).getMethod(TypeId.DOUBLE, "doubleValue"));
        map.put(char.class, TypeId.get(Character.class).getMethod(TypeId.CHAR, "charValue"));
        PRIMITIVE_TO_UNBOX_METHOD = map;
    }

    private static class MethodSetEntry {
        private final String name;
        private final Class<?>[] paramTypes;
        private final Class<?> returnType;
        private final Method originalMethod;

        public MethodSetEntry(Method method) {
            originalMethod = method;
            name = method.getName();
            paramTypes = method.getParameterTypes();
            returnType = method.getReturnType();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof MethodSetEntry) {
                MethodSetEntry other = (MethodSetEntry) o;
                return name.equals(other.name)
                        && returnType.equals(other.returnType)
                        && Arrays.equals(paramTypes, other.paramTypes);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result += 31 * result + name.hashCode();
            result += 31 * result + returnType.hashCode();
            result += 31 * result + Arrays.hashCode(paramTypes);
            return result;
        }
    }
}
