
package com.android.cglib.dx.util;

public final class Uint implements Comparable<Uint> {
    public final int intValue;

    public Uint(int value) {
        this.intValue = value;
    }

    public int compareTo(Uint uint) {
        return Unsigned.compare(intValue, uint.intValue);
    }
}
