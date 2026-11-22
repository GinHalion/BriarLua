
package com.android.cglib.dx.io.instructions;

import java.io.EOFException;

public final class ShortArrayCodeInput extends BaseCodeCursor
        implements CodeInput {
    private final short[] array;

    public ShortArrayCodeInput(short[] array) {
        if (array == null) {
            throw new NullPointerException("array == null");
        }

        this.array = array;
    }

    public boolean hasMore() {
        return cursor() < array.length;
    }

    public int read() throws EOFException {
        try {
            int value = array[cursor()];
            advance(1);
            return value & 0xffff;
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new EOFException();
        }
    }

    public int readInt() throws EOFException {
        int short0 = read();
        int short1 = read();

        return short0 | (short1 << 16);
    }

    public long readLong() throws EOFException {
        long short0 = read();
        long short1 = read();
        long short2 = read();
        long short3 = read();

        return short0 | (short1 << 16) | (short2 << 32) | (short3 << 48);
    }
}
