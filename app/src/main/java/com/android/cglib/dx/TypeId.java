
package com.android.cglib.dx;

import java.util.HashMap;
import java.util.Map;

import com.android.cglib.dx.rop.cst.CstType;

public final class TypeId<T> {
    public static final TypeId<Boolean> BOOLEAN
            = new TypeId<Boolean>(com.android.cglib.dx.rop.type.Type.BOOLEAN);

    public static final TypeId<Byte> BYTE = new TypeId<Byte>(com.android.cglib.dx.rop.type.Type.BYTE);

    public static final TypeId<Character> CHAR
            = new TypeId<Character>(com.android.cglib.dx.rop.type.Type.CHAR);

    public static final TypeId<Double> DOUBLE = new TypeId<Double>(com.android.cglib.dx.rop.type.Type.DOUBLE);

    public static final TypeId<Float> FLOAT = new TypeId<Float>(com.android.cglib.dx.rop.type.Type.FLOAT);

    public static final TypeId<Integer> INT = new TypeId<Integer>(com.android.cglib.dx.rop.type.Type.INT);

    public static final TypeId<Long> LONG = new TypeId<Long>(com.android.cglib.dx.rop.type.Type.LONG);

    public static final TypeId<Short> SHORT = new TypeId<Short>(com.android.cglib.dx.rop.type.Type.SHORT);

    public static final TypeId<Void> VOID = new TypeId<Void>(com.android.cglib.dx.rop.type.Type.VOID);

    public static final TypeId<Object> OBJECT = new TypeId<Object>(com.android.cglib.dx.rop.type.Type.OBJECT);

    public static final TypeId<String> STRING = new TypeId<String>(com.android.cglib.dx.rop.type.Type.STRING);

    private static final Map<Class<?>, TypeId<?>> PRIMITIVE_TO_TYPE
            = new HashMap<Class<?>, TypeId<?>>();
    static {
        PRIMITIVE_TO_TYPE.put(boolean.class, BOOLEAN);
        PRIMITIVE_TO_TYPE.put(byte.class, BYTE);
        PRIMITIVE_TO_TYPE.put(char.class, CHAR);
        PRIMITIVE_TO_TYPE.put(double.class, DOUBLE);
        PRIMITIVE_TO_TYPE.put(float.class, FLOAT);
        PRIMITIVE_TO_TYPE.put(int.class, INT);
        PRIMITIVE_TO_TYPE.put(long.class, LONG);
        PRIMITIVE_TO_TYPE.put(short.class, SHORT);
        PRIMITIVE_TO_TYPE.put(void.class, VOID);
    }

    final String name;

    final com.android.cglib.dx.rop.type.Type ropType;
    final CstType constant;

    TypeId(com.android.cglib.dx.rop.type.Type ropType) {
        this(ropType.getDescriptor(), ropType);
    }

    TypeId(String name, com.android.cglib.dx.rop.type.Type ropType) {
        if (name == null || ropType == null) {
            throw new NullPointerException();
        }
        this.name = name;
        this.ropType = ropType;
        this.constant = CstType.intern(ropType);
    }

    public static <T> TypeId<T> get(String name) {
        return new TypeId<T>(name, com.android.cglib.dx.rop.type.Type.internReturnType(name));
    }

    public static <T> TypeId<T> get(Class<T> type) {
        if (type.isPrimitive()) {
            @SuppressWarnings("unchecked")
                    TypeId<T> result = (TypeId<T>) PRIMITIVE_TO_TYPE.get(type);
            return result;
        }
        String name = type.getName().replace('.', '/');
        return get(type.isArray() ? name : 'L' + name + ';');
    }

    public <V> FieldId<T, V> getField(TypeId<V> type, String name) {
        return new FieldId<T, V>(this, type, name);
    }

    public MethodId<T, Void> getConstructor(TypeId<?>... parameters) {
        return new MethodId<T, Void>(this, VOID, "<init>", new TypeList(parameters));
    }

    public <R> MethodId<T, R> getMethod(TypeId<R> returnType, String name, TypeId<?>... parameters) {
        return new MethodId<T, R>(this, returnType, name, new TypeList(parameters));
    }

    public String getName() {
        return name;
    }

    @Override public boolean equals(Object o) {
        return o instanceof TypeId
                && ((TypeId) o).name.equals(name);
    }

    @Override public int hashCode() {
        return name.hashCode();
    }

    @Override public String toString() {
        return name;
    }
}
