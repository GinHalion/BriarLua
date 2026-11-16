
package com.android.cglib.dx.dex.code;

import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.code.SourcePosition;

public final class CodeAddress extends ZeroSizeInsn {
    public CodeAddress(SourcePosition position) {
        super(position);
    }

    @Override
    public final DalvInsn withRegisters(RegisterSpecList registers) {
        return new CodeAddress(getPosition());
    }

    @Override
    protected String argString() {
        return null;
    }

    @Override
    protected String listingString0(boolean noteIndices) {
        return "code-address";
    }
}
