
package com.android.cglib.dx.rop.code;

import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.type.Type;
import com.android.cglib.dx.rop.type.TypeList;

public final class ThrowingCstInsn
        extends CstInsn {
    private final TypeList catches;

    public ThrowingCstInsn(Rop opcode, SourcePosition position,
                           RegisterSpecList sources,
                           TypeList catches, Constant cst) {
        super(opcode, position, null, sources, cst);

        if (opcode.getBranchingness() != Rop.BRANCH_THROW) {
            throw new IllegalArgumentException("bogus branchingness");
        }

        if (catches == null) {
            throw new NullPointerException("catches == null");
        }

        this.catches = catches;
    }

    @Override
    public String getInlineString() {
        Constant cst = getConstant();
        String constantString = cst.toHuman();
        if (cst instanceof CstString) {
            constantString = ((CstString) cst).toQuoted();
        }
        return constantString + " " + ThrowingInsn.toCatchString(catches);
    }

    @Override
    public TypeList getCatches() {
        return catches;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitThrowingCstInsn(this);
    }

    @Override
    public Insn withAddedCatch(Type type) {
        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   getSources(), catches.withAddedType(type),
                                   getConstant());
    }

    @Override
    public Insn withRegisterOffset(int delta) {
        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   getSources().withOffset(delta),
                                   catches,
                                   getConstant());
    }

    @Override
    public Insn withNewRegisters(RegisterSpec result,
            RegisterSpecList sources) {

        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   sources,
                                   catches,
                                   getConstant());
    }


}
