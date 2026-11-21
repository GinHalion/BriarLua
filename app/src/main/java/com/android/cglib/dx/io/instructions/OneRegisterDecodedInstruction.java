
package com.android.cglib.dx.io.instructions;

import com.android.cglib.dx.io.IndexType;

public final class OneRegisterDecodedInstruction extends DecodedInstruction {
    private final int a;

    public OneRegisterDecodedInstruction(InstructionCodec format, int opcode,
            int index, IndexType indexType, int target, long literal,
            int a) {
        super(format, opcode, index, indexType, target, literal);

        this.a = a;
    }

    public int getRegisterCount() {
        return 1;
    }

    public int getA() {
        return a;
    }

    public DecodedInstruction withIndex(int newIndex) {
        return new OneRegisterDecodedInstruction(
                getFormat(), getOpcode(), newIndex, getIndexType(),
                getTarget(), getLiteral(), a);
    }
}
