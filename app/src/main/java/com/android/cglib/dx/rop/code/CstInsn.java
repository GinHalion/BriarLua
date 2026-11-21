
package com.android.cglib.dx.rop.code;

import com.android.cglib.dx.rop.cst.Constant;

public abstract class CstInsn
        extends Insn {
    private final Constant cst;

    public CstInsn(Rop opcode, SourcePosition position, RegisterSpec result,
                   RegisterSpecList sources, Constant cst) {
        super(opcode, position, result, sources);

        if (cst == null) {
            throw new NullPointerException("cst == null");
        }

        this.cst = cst;
    }

    @Override
    public String getInlineString() {
        return cst.toHuman();
    }

    public Constant getConstant() {
        return cst;
    }

    @Override
    public boolean contentEquals(Insn b) {
        return super.contentEquals(b)
                && cst.equals(((CstInsn)b).getConstant());
    }
}
