
package com.android.cglib.dx.io.instructions;

public final class PackedSwitchPayloadDecodedInstruction
        extends DecodedInstruction {
    private final int firstKey;

    private final int[] targets;

    public PackedSwitchPayloadDecodedInstruction(InstructionCodec format,
            int opcode, int firstKey, int[] targets) {
        super(format, opcode, 0, null, 0, 0L);

        this.firstKey = firstKey;
        this.targets = targets;
    }

    public int getRegisterCount() {
        return 0;
    }

    public int getFirstKey() {
        return firstKey;
    }

    public int[] getTargets() {
        return targets;
    }

    public DecodedInstruction withIndex(int newIndex) {
        throw new UnsupportedOperationException("no index in instruction");
    }
}
