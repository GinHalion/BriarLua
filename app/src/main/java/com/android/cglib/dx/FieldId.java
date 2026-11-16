
package com.android.cglib.dx;

import com.android.cglib.dx.rop.cst.CstFieldRef;
import com.android.cglib.dx.rop.cst.CstNat;
import com.android.cglib.dx.rop.cst.CstString;

public final class FieldId<D, V> {
    final TypeId<D> declaringType;
    final TypeId<V> type;
    final String name;

    final CstNat nat;
    final CstFieldRef constant;

    FieldId(TypeId<D> declaringType, TypeId<V> type, String name) {
        if (declaringType == null || type == null || name == null) {
            throw new NullPointerException();
        }
        this.declaringType = declaringType;
        this.type = type;
        this.name = name;
        this.nat = new CstNat(new CstString(name), new CstString(type.name));
        this.constant = new CstFieldRef(declaringType.constant, nat);
    }

    public TypeId<D> getDeclaringType() {
        return declaringType;
    }

    public TypeId<V> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override public boolean equals(Object o) {
        return o instanceof FieldId
                && ((FieldId<?, ?>) o).declaringType.equals(declaringType)
                && ((FieldId<?, ?>) o).name.equals(name);
    }

    @Override public int hashCode() {
        return declaringType.hashCode() + 37 * name.hashCode();
    }

    @Override public String toString() {
        return declaringType + "." + name;
    }
}
