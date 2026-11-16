
package com.android.cglib.dx.dex.code;

import com.android.cglib.dx.io.Opcodes;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.code.SourcePosition;
import com.android.cglib.dx.util.AnnotatedOutput;

public final class OddSpacer extends VariableSizeInsn {
    public OddSpacer(SourcePosition position) {
        super(position, RegisterSpecList.EMPTY);
    }

    @Override
    public int codeSize() {
        return (getAddress() & 1);
    }

    @Override
    public void writeTo(AnnotatedOutput out) {
        if (codeSize() != 0) {
            out.writeShort(InsnFormat.codeUnit(Opcodes.NOP, 0));
        }
    }

    @Override
    public DalvInsn withRegisters(RegisterSpecList registers) {
        return new OddSpacer(getPosition());
    }

    @Override
    protected String argString() {
        return null;
    }

    @Override
    protected String listingString0(boolean noteIndices) {
        if (codeSize() == 0) {
            return null;
        }

        return "nop // spacer";
    }
}
