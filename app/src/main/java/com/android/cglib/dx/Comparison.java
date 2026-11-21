
package com.android.cglib.dx;

import com.android.cglib.dx.rop.code.Rop;
import com.android.cglib.dx.rop.code.Rops;
import com.android.cglib.dx.rop.type.TypeList;

public enum Comparison {

    LT() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfLt(types);
        }
    },

    LE() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfLe(types);
        }
    },

    EQ() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfEq(types);
        }
    },

    GE() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfGe(types);
        }
    },

    GT() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfGt(types);
        }
    },

    NE() {
        @Override Rop rop(TypeList types) {
            return Rops.opIfNe(types);
        }
    };

    abstract Rop rop(TypeList types);
}
