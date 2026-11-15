
package com.android.cglib.dx.dex.code.form;

import java.util.BitSet;

import com.android.cglib.dx.dex.code.DalvInsn;
import com.android.cglib.dx.dex.code.InsnFormat;
import com.android.cglib.dx.dex.code.SimpleInsn;
import com.android.cglib.dx.rop.code.RegisterSpec;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.util.AnnotatedOutput;

public final class Form12x extends InsnFormat {
    public static final InsnFormat THE_ONE = new Form12x();

    private Form12x() {
    }

    @Override
    public String insnArgString(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int sz = regs.size();


        return regs.get(sz - 2).regString() + ", " +
            regs.get(sz - 1).regString();
    }

    @Override
    public String insnCommentString(DalvInsn insn, boolean noteIndices) {
        return "";
    }

    @Override
    public int codeSize() {
        return 1;
    }

    @Override
    public boolean isCompatible(DalvInsn insn) {
        if (!(insn instanceof SimpleInsn)) {
            return false;
        }

        RegisterSpecList regs = insn.getRegisters();
        RegisterSpec rs1;
        RegisterSpec rs2;

        switch (regs.size()) {
            case 2: {
                rs1 = regs.get(0);
                rs2 = regs.get(1);
                break;
            }
            case 3: {
                rs1 = regs.get(1);
                rs2 = regs.get(2);
                if (rs1.getReg() != regs.get(0).getReg()) {
                    return false;
                }
                break;
            }
            default: {
                return false;
            }
        }

        return unsignedFitsInNibble(rs1.getReg()) &&
            unsignedFitsInNibble(rs2.getReg());
    }

    @Override
    public BitSet compatibleRegs(DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        BitSet bits = new BitSet(2);

        bits.set(0, unsignedFitsInNibble(regs.get(0).getReg()));
        bits.set(1, unsignedFitsInNibble(regs.get(1).getReg()));
        return bits;
    }

    @Override
    public void writeTo(AnnotatedOutput out, DalvInsn insn) {
        RegisterSpecList regs = insn.getRegisters();
        int sz = regs.size();


        write(out, opcodeUnit(insn,
                              makeByte(regs.get(sz - 2).getReg(),
                                       regs.get(sz - 1).getReg())));
    }
}
