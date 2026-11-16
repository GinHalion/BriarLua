
package com.android.cglib.dx.ssa.back;

import java.util.ArrayList;

import com.android.cglib.dx.rop.code.PlainInsn;
import com.android.cglib.dx.rop.code.RegOps;
import com.android.cglib.dx.rop.code.RegisterSpec;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.code.Rops;
import com.android.cglib.dx.rop.code.SourcePosition;
import com.android.cglib.dx.ssa.NormalSsaInsn;
import com.android.cglib.dx.ssa.RegisterMapper;
import com.android.cglib.dx.ssa.SsaBasicBlock;
import com.android.cglib.dx.ssa.SsaInsn;
import com.android.cglib.dx.ssa.SsaMethod;
import com.android.cglib.dx.util.IntIterator;
import com.android.cglib.dx.util.IntSet;

public abstract class RegisterAllocator {
    protected final SsaMethod ssaMeth;

    protected final InterferenceGraph interference;

    public RegisterAllocator(SsaMethod ssaMeth,
            InterferenceGraph interference) {
        this.ssaMeth = ssaMeth;
        this.interference = interference;
    }

    public abstract boolean wantsParamsMovedHigh();

    public abstract RegisterMapper allocateRegisters();

    protected final int getCategoryForSsaReg(int reg) {
        SsaInsn definition = ssaMeth.getDefinitionForRegister(reg);

        if (definition == null) {
            return 1;
        } else {
            return definition.getResult().getCategory();
        }
    }

    protected final RegisterSpec getDefinitionSpecForSsaReg(int reg) {
        SsaInsn definition = ssaMeth.getDefinitionForRegister(reg);

        return definition == null ? null : definition.getResult();
    }

    protected boolean isDefinitionMoveParam(int reg) {
        SsaInsn defInsn = ssaMeth.getDefinitionForRegister(reg);

        if (defInsn instanceof NormalSsaInsn) {
            NormalSsaInsn ndefInsn = (NormalSsaInsn) defInsn;

            return ndefInsn.getOpcode().getOpcode() == RegOps.MOVE_PARAM;
        }

        return false;
    }

    protected final RegisterSpec insertMoveBefore(SsaInsn insn,
            RegisterSpec reg) {
        SsaBasicBlock block = insn.getBlock();
        ArrayList<SsaInsn> insns = block.getInsns();
        int insnIndex = insns.indexOf(insn);

        if (insnIndex < 0) {
            throw new IllegalArgumentException (
                    "specified insn is not in this block");
        }

        if (insnIndex != insns.size() - 1) {
            throw new IllegalArgumentException(
                    "Adding move here not supported:" + insn.toHuman());
        }


        RegisterSpec newRegSpec = RegisterSpec.make(ssaMeth.makeNewSsaReg(),
                reg.getTypeBearer());

        SsaInsn toAdd = SsaInsn.makeFromRop(
                new PlainInsn(Rops.opMove(newRegSpec.getType()),
                        SourcePosition.NO_INFO, newRegSpec,
                        RegisterSpecList.make(reg)), block);

        insns.add(insnIndex, toAdd);

        int newReg = newRegSpec.getReg();


        IntSet liveOut = block.getLiveOutRegs();
        IntIterator liveOutIter = liveOut.iterator();

        while (liveOutIter.hasNext()) {
            interference.add(newReg, liveOutIter.next());
        }

        RegisterSpecList sources = insn.getSources();
        int szSources = sources.size();

        for (int i = 0; i < szSources; i++) {
            interference.add(newReg, sources.get(i).getReg());
        }

        ssaMeth.onInsnsChanged();

        return newRegSpec;
    }
}
