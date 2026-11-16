
package com.android.cglib.dx.io.instructions;

import com.android.cglib.dx.io.IndexType;

public final class FiveRegisterDecodedInstruction extends DecodedInstruction {
    private final int a;

    private final int b;

    private final int c;

    private final int d;

    private final int e;

    public FiveRegisterDecodedInstruction(InstructionCodec format, int opcode,
            int index, IndexType indexType, int target, long literal,
            int a, int b, int c, int d, int e) {
        super(format, opcode, index, indexType, target, literal);

        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
    }

    public int getRegisterCount() {
        return 5;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }

    public int getC() {
        return c;
    }

    public int getD() {
        return d;
    }

    public int getE() {
        return e;
    }

    public DecodedInstruction withIndex(int newIndex) {
        return new FiveRegisterDecodedInstruction(
                getFormat(), getOpcode(), newIndex, getIndexType(),
                getTarget(), getLiteral(), a, b, c, d, e);
    }
}
