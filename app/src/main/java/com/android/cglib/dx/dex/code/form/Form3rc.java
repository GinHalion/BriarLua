
package com.android.cglib.dx.dex.code.form;

import com.android.cglib.dx.dex.code.CstInsn;
import com.android.cglib.dx.dex.code.DalvInsn;
import com.android.cglib.dx.dex.code.InsnFormat;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstMethodRef;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.util.AnnotatedOutput;

public final class Form3rc extends InsnFormat {
    public static final InsnFormat THE_ONE = new Form3rc();

    private Form3rc() {
    }

    @Override
    public String insnArgString(DalvInsn insn) {
        return regRangeString(insn.getRegisters()) + ", " +
            cstString(insn);
    }

    @Override
    public String insnCommentString(DalvInsn insn, boolean noteIndices) {
        if (noteIndices) {
            return cstComment(insn);
        } else {
            return "";
        }
    }

    @Override
    public int codeSize() {
        return 3;
    }

    @Override
    public boolean isCompatible(DalvInsn insn) {
        if (!(insn instanceof CstInsn)) {
            return false;
        }

        CstInsn ci = (CstInsn) insn;
        int cpi = ci.getIndex();
        Constant cst = ci.getConstant();

        if (! unsignedFitsInShort(cpi)) {
            return false;
        }

        if (!((cst instanceof CstMethodRef) ||
              (cst instanceof CstType))) {
            return false;
        }

        RegisterSpecList regs = ci.getRegisters();
        int sz = regs.size();

        return (regs.size() == 0) ||
            (isRegListSequential(regs) &&
             unsignedFitsInShort(regs.get(0).getReg()) &&
             unsignedFitsInByte(regs.getWordCount()));
    }

    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int cpi = ((CstInsn) insn).getIndex();
        int firstReg = (regs.size() == 0) ? 0 : regs.get(0).getReg();
        int count = regs.getWordCount();

        write(out, opcodeUnit(insn, count), (short) cpi, (short) firstReg);
    }
}
