
package com.android.cglib.dx.io.instructions;

import com.android.cglib.dx.io.IndexType;

public final class RegisterRangeDecodedInstruction extends DecodedInstruction {
    private final int a;

    private final int registerCount;

    public RegisterRangeDecodedInstruction(InstructionCodec format, int opcode,
            int index, IndexType indexType, int target, long literal,
            int a, int registerCount) {
        super(format, opcode, index, indexType, target, literal);

        this.a = a;
        this.registerCount = registerCount;
    }

    public int getRegisterCount() {
        return registerCount;
    }

    public int getA() {
        return a;
    }

    public DecodedInstruction withIndex(int newIndex) {
        return new RegisterRangeDecodedInstruction(
                getFormat(), getOpcode(), newIndex, getIndexType(),
                getTarget(), getLiteral(), a, registerCount);
    }
}
