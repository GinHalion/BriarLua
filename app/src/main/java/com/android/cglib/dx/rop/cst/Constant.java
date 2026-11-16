
package com.android.cglib.dx.rop.cst;

import com.android.cglib.dx.util.ToHuman;

public abstract class Constant
        implements ToHuman, Comparable<Constant> {
    public abstract boolean isCategory2();

    public abstract String typeName();

    public final int compareTo(Constant other) {
        Class clazz = getClass();
        Class otherClazz = other.getClass();

        if (clazz != otherClazz) {
            return clazz.getName().compareTo(otherClazz.getName());
        }

        return compareTo0(other);
    }

    protected abstract int compareTo0(Constant other);
}
