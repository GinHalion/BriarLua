
package com.android.cglib.dx;

import com.android.cglib.dx.rop.code.Rop;
import com.android.cglib.dx.rop.code.Rops;

public enum UnaryOp {

    NOT() {
        @Override Rop rop(TypeId<?> type) {
            return Rops.opNot(type.ropType);
        }
    },

    NEGATE() {
        @Override Rop rop(TypeId<?> type) {
            return Rops.opNeg(type.ropType);
        }
    };

    abstract Rop rop(TypeId<?> type);
}
