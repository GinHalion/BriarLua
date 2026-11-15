
package com.android.cglib.dx.dex.code.form;

import java.util.BitSet;

import com.android.cglib.dx.dex.code.CstInsn;
import com.android.cglib.dx.dex.code.DalvInsn;
import com.android.cglib.dx.dex.code.InsnFormat;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstFieldRef;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.util.AnnotatedOutput;

public final class Form52c extends InsnFormat {
    public static final InsnFormat THE_ONE = new Form52c();

    private Form52c() {
    }

    @Override
    public String insnArgString(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        return regs.get(0).regString() + ", " + regs.get(1).regString() +
            ", " + cstString(insn);
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
        return 5;
    }

    @Override
    public boolean isCompatible(DalvInsn insn) {
        if (! ALLOW_EXTENDED_OPCODES) {
            return false;
        }

        RegisterSpecList regs = insn.getRegisters();
        if (!((insn instanceof CstInsn) &&
              (regs.size() == 2) &&
              unsignedFitsInShort(regs.get(0).getReg()) &&
              unsignedFitsInShort(regs.get(1).getReg()))) {
            return false;
        }

        CstInsn ci = (CstInsn) insn;
        Constant cst = ci.getConstant();

        return (cst instanceof CstType) ||
            (cst instanceof CstFieldRef);
    }

    @Override
    public BitSet compatibleRegs(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        BitSet bits = new BitSet(2);

        bits.set(0, unsignedFitsInShort(regs.get(0).getReg()));
        bits.set(1, unsignedFitsInShort(regs.get(1).getReg()));
        return bits;
    }

    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int cpi = ((CstInsn) insn).getIndex();

        write(out,
                opcodeUnit(insn),
                cpi,
                (short) regs.get(0).getReg(),
                (short) regs.get(1).getReg());
    }
}
