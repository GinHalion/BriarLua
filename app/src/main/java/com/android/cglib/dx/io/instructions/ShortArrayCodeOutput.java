
package com.android.cglib.dx.io.instructions;

public final class ShortArrayCodeOutput extends BaseCodeCursor
        implements CodeOutput {
    private final short[] array;

    public ShortArrayCodeOutput(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize < 0");
        }

        this.array = new short[maxSize];
    }

    public short[] getArray() {
        int cursor = cursor();

        if (cursor == array.length) {
            return array;
        }

        short[] result = new short[cursor];
        System.arraycopy(array, 0, result, 0, cursor);
        return result;
    }

    public void write(short codeUnit) {
        array[cursor()] = codeUnit;
        advance(1);
    }

    public void write(short u0, short u1) {
        write(u0);
        write(u1);
    }

    public void write(short u0, short u1, short u2) {
        write(u0);
        write(u1);
        write(u2);
    }

    public void write(short u0, short u1, short u2, short u3) {
        write(u0);
        write(u1);
        write(u2);
        write(u3);
    }

    public void write(short u0, short u1, short u2, short u3, short u4) {
        write(u0);
        write(u1);
        write(u2);
        write(u3);
        write(u4);
    }

    public void writeInt(int value) {
        write((short) value);
        write((short) (value >> 16));
    }

    public void writeLong(long value) {
        write((short) value);
        write((short) (value >> 16));
        write((short) (value >> 32));
        write((short) (value >> 48));
    }

    public void write(byte[] data) {
        int value = 0;
        boolean even = true;
        for (byte b : data) {
            if (even) {
                value = b & 0xff;
                even = false;
            } else {
                value |= b << 8;
                write((short) value);
                even = true;
            }
        }

        if (!even) {
            write((short) value);
        }
    }

    public void write(short[] data) {
        for (short unit : data) {
            write(unit);
        }
    }

    public void write(int[] data) {
        for (int i : data) {
            writeInt(i);
        }
    }

    public void write(long[] data) {
        for (long l : data) {
            writeLong(l);
        }
    }
}
