
package com.android.cglib.dx.dex.code;

import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.code.SourcePosition;

public abstract class VariableSizeInsn extends DalvInsn {
    public VariableSizeInsn(SourcePosition position,
                            RegisterSpecList registers) {
        super(Dops.SPECIAL_FORMAT, position, registers);
    }

    @Override
    public final DalvInsn withOpcode(Dop opcode) {
        throw new RuntimeException("unsupported");
    }

    @Override
    public final DalvInsn withRegisterOffset(int delta) {
        return withRegisters(getRegisters().withOffset(delta));
    }
}
