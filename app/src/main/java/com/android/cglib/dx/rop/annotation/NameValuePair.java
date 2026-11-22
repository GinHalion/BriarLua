
package com.android.cglib.dx.rop.annotation;

import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstString;

public final class NameValuePair implements Comparable<NameValuePair> {
    private final CstString name;

    private final Constant value;

    public NameValuePair(CstString name, Constant value) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }

        if (value == null) {
            throw new NullPointerException("value == null");
        }

        this.name = name;
        this.value = value;
    }

    public String toString() {
        return name.toHuman() + ":" + value;
    }

    public int hashCode() {
        return name.hashCode() * 31 + value.hashCode();
    }

    public boolean equals(Object other) {
        if (! (other instanceof NameValuePair)) {
            return false;
        }

        NameValuePair otherPair = (NameValuePair) other;

        return name.equals(otherPair.name)
            && value.equals(otherPair.value);
    }

    public int compareTo(NameValuePair other) {
        int result = name.compareTo(other.name);

        if (result != 0) {
            return result;
        }

        return value.compareTo(other.value);
    }

    public CstString getName() {
        return name;
    }

    public Constant getValue() {
        return value;
    }
}
