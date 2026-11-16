
package com.android.cglib.dx.rop.cst;

import com.android.cglib.dx.rop.type.Type;

public final class CstNat extends Constant {
    public static final CstNat PRIMITIVE_TYPE_NAT =
        new CstNat(new CstString("TYPE"),
                   new CstString("Ljava/lang/Class;"));

    private final CstString name;

    private final CstString descriptor;

    public CstNat(CstString name, CstString descriptor) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (descriptor == null) {
            throw new NullPointerException("descriptor == null");
        }

        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CstNat)) {
            return false;
        }

        CstNat otherNat = (CstNat) other;
        return name.equals(otherNat.name) &&
            descriptor.equals(otherNat.descriptor);
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31) ^ descriptor.hashCode();
    }

    @Override
    protected int compareTo0(Constant other) {
        CstNat otherNat = (CstNat) other;
        int cmp = name.compareTo(otherNat.name);

        if (cmp != 0) {
            return cmp;
        }

        return descriptor.compareTo(otherNat.descriptor);
    }

    @Override
    public String toString() {
        return "nat{" + toHuman() + '}';
    }

    @Override
    public String typeName() {
        return "nat";
    }

    @Override
    public boolean isCategory2() {
        return false;
    }

    public CstString getName() {
        return name;
    }

    public CstString getDescriptor() {
        return descriptor;
    }

    public String toHuman() {
        return name.toHuman() + ':' + descriptor.toHuman();
    }

    public Type getFieldType() {
        return Type.intern(descriptor.getString());
    }

    public final boolean isInstanceInit() {
        return name.getString().equals("<init>");
    }

    public final boolean isClassInit() {
        return name.getString().equals("<clinit>");
    }
}
