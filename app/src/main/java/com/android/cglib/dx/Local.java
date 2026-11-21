
package com.android.cglib.dx;

import com.android.cglib.dx.rop.code.RegisterSpec;

public final class Local<T> {
    private final Code code;
    final TypeId<T> type;
    private int reg = -1;
    private RegisterSpec spec;

    private Local(Code code, TypeId<T> type) {
        this.code = code;
        this.type = type;
    }

    static <T> Local<T> get(Code code, TypeId<T> type) {
        return new Local<T>(code, type);
    }

    int initialize(int nextAvailableRegister) {
        this.reg = nextAvailableRegister;
        this.spec = RegisterSpec.make(nextAvailableRegister, type.ropType);
        return size();
    }

    int size() {
        return type.ropType.getCategory();
    }

    RegisterSpec spec() {
        if (spec == null) {
            code.initializeLocals();
            if (spec == null) {
                throw new AssertionError();
            }
        }
        return spec;
    }

    public TypeId getType() {
        return type;
    }

    @Override public String toString() {
        return "v" + reg + "(" + type + ")";
    }
}
