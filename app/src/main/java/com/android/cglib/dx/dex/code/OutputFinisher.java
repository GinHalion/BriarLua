
package com.android.cglib.dx.dex.code;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;

import com.android.cglib.dx.dex.DexOptions;
import com.android.cglib.dx.io.Opcodes;
import com.android.cglib.dx.rop.code.LocalItem;
import com.android.cglib.dx.rop.code.RegisterSpec;
import com.android.cglib.dx.rop.code.RegisterSpecList;
import com.android.cglib.dx.rop.code.RegisterSpecSet;
import com.android.cglib.dx.rop.code.SourcePosition;
import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstMemberRef;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.rop.type.Type;
import com.android.cglib.dx.util.DexException;

public final class OutputFinisher {
    private final DexOptions dexOptions;

    private final int unreservedRegCount;

    private ArrayList<DalvInsn> insns;

    private boolean hasAnyPositionInfo;

    private boolean hasAnyLocalInfo;

    private int reservedCount;

    public OutputFinisher(DexOptions dexOptions, int initialCapacity, int regCount) {
        this.dexOptions = dexOptions;
        this.unreservedRegCount = regCount;
        this.insns = new ArrayList<DalvInsn>(initialCapacity);
        this.reservedCount = -1;
        this.hasAnyPositionInfo = false;
        this.hasAnyLocalInfo = false;
    }

    public boolean hasAnyPositionInfo() {
        return hasAnyPositionInfo;
    }

    public boolean hasAnyLocalInfo() {
        return hasAnyLocalInfo;
    }

    private static boolean hasLocalInfo(DalvInsn insn) {
        if (insn instanceof LocalSnapshot) {
            RegisterSpecSet specs = ((LocalSnapshot) insn).getLocals();
            int size = specs.size();
            for (int i = 0; i < size; i++) {
                if (hasLocalInfo(specs.get(i))) {
                    return true;
                }
            }
        } else if (insn instanceof LocalStart) {
            RegisterSpec spec = ((LocalStart) insn).getLocal();
            if (hasLocalInfo(spec)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasLocalInfo(RegisterSpec spec) {
        return (spec != null)
            && (spec.getLocalItem().getName() != null);
    }

    public HashSet<Constant> getAllConstants() {
        HashSet<Constant> result = new HashSet<Constant>(20);

        for (DalvInsn insn : insns) {
            addConstants(result, insn);
        }

        return result;
    }

    private static void addConstants(HashSet<Constant> result,
            DalvInsn insn) {
        if (insn instanceof CstInsn) {
            Constant cst = ((CstInsn) insn).getConstant();
            result.add(cst);
        } else if (insn instanceof LocalSnapshot) {
            RegisterSpecSet specs = ((LocalSnapshot) insn).getLocals();
            int size = specs.size();
            for (int i = 0; i < size; i++) {
                addConstants(result, specs.get(i));
            }
        } else if (insn instanceof LocalStart) {
            RegisterSpec spec = ((LocalStart) insn).getLocal();
            addConstants(result, spec);
        }
    }

    private static void addConstants(HashSet<Constant> result,
            RegisterSpec spec) {
        if (spec == null) {
            return;
        }

        LocalItem local = spec.getLocalItem();
        CstString name = local.getName();
        CstString signature = local.getSignature();
        Type type = spec.getType();

        if (type != Type.KNOWN_NULL) {
            result.add(CstType.intern(type));
        }

        if (name != null) {
            result.add(name);
        }

        if (signature != null) {
            result.add(signature);
        }
    }

    public void add(DalvInsn insn) {
        insns.add(insn);
        updateInfo(insn);
    }

    public void insert(int at, DalvInsn insn) {
        insns.add(at, insn);
        updateInfo(insn);
    }

    private void updateInfo(DalvInsn insn) {
        if (! hasAnyPositionInfo) {
            SourcePosition pos = insn.getPosition();
            if (pos.getLine() >= 0) {
                hasAnyPositionInfo = true;
            }
        }

        if (! hasAnyLocalInfo) {
            if (hasLocalInfo(insn)) {
                hasAnyLocalInfo = true;
            }
        }
    }

    public void reverseBranch(int which, CodeAddress newTarget) {
        int size = insns.size();
        int index = size - which - 1;
        TargetInsn targetInsn;

        try {
            targetInsn = (TargetInsn) insns.get(index);
        } catch (IndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("too few instructions");
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException("non-reversible instruction");
        }

        insns.set(index, targetInsn.withNewTargetAndReversed(newTarget));
    }

    public void assignIndices(DalvCode.AssignIndicesCallback callback) {
        for (DalvInsn insn : insns) {
            if (insn instanceof CstInsn) {
                assignIndices((CstInsn) insn, callback);
            }
        }
    }

    private static void assignIndices(CstInsn insn,
            DalvCode.AssignIndicesCallback callback) {
        Constant cst = insn.getConstant();
        int index = callback.getIndex(cst);

        if (index >= 0) {
            insn.setIndex(index);
        }

        if (cst instanceof CstMemberRef) {
            CstMemberRef member = (CstMemberRef) cst;
            CstType definer = member.getDefiningClass();
            index = callback.getIndex(definer);
            if (index >= 0) {
                insn.setClassIndex(index);
            }
        }
    }

    public DalvInsnList finishProcessingAndGetList() {
        if (reservedCount >= 0) {
            throw new UnsupportedOperationException("already processed");
        }

        Dop[] opcodes = makeOpcodesArray();
        reserveRegisters(opcodes);
        massageInstructions(opcodes);
        assignAddressesAndFixBranches();

        return DalvInsnList.makeImmutable(insns,
                reservedCount + unreservedRegCount);
    }

    private Dop[] makeOpcodesArray() {
        int size = insns.size();
        Dop[] result = new Dop[size];

        for (int i = 0; i < size; i++) {
            result[i] = insns.get(i).getOpcode();
        }

        return result;
    }

    private void reserveRegisters(Dop[] opcodes) {
        int oldReservedCount = (reservedCount < 0) ? 0 : reservedCount;

        for (;;) {
            int newReservedCount = calculateReservedCount(opcodes);
            if (oldReservedCount >= newReservedCount) {
                break;
            }

            int reservedDifference = newReservedCount - oldReservedCount;
            int size = insns.size();

            for (int i = 0; i < size; i++) {
                DalvInsn insn = insns.get(i);
                if (!(insn instanceof CodeAddress)) {
                    insns.set(i, insn.withRegisterOffset(reservedDifference));
                }
            }

            oldReservedCount = newReservedCount;
        }

        reservedCount = oldReservedCount;
    }

    private int calculateReservedCount(Dop[] opcodes) {
        int size = insns.size();

        int newReservedCount = reservedCount;

        for (int i = 0; i < size; i++) {
            DalvInsn insn = insns.get(i);
            Dop originalOpcode = opcodes[i];
            Dop newOpcode = findOpcodeForInsn(insn, originalOpcode);

            if (newOpcode == null) {
                Dop expandedOp = findExpandedOpcodeForInsn(insn);
                BitSet compatRegs = expandedOp.getFormat().compatibleRegs(insn);
                int reserve = insn.getMinimumRegisterRequirement(compatRegs);
                if (reserve > newReservedCount) {
                    newReservedCount = reserve;
                }
            } else if (originalOpcode == newOpcode) {
                continue;
            }

            opcodes[i] = newOpcode;
        }

        return newReservedCount;
    }

    private Dop findOpcodeForInsn(DalvInsn insn, Dop guess) {

        while (guess != null) {
            if (guess.getFormat().isCompatible(insn)) {
                break;
            }

            guess = Dops.getNextOrNull(guess, dexOptions);
        }

        return guess;
    }

    private Dop findExpandedOpcodeForInsn(DalvInsn insn) {
        Dop result = findOpcodeForInsn(insn.getLowRegVersion(), insn.getOpcode());
        if (result == null) {
            throw new DexException("No expanded opcode for " + insn);
        }
        return result;
    }

    private void massageInstructions(Dop[] opcodes) {
        if (reservedCount == 0) {
            int size = insns.size();

            for (int i = 0; i < size; i++) {
                DalvInsn insn = insns.get(i);
                Dop originalOpcode = insn.getOpcode();
                Dop currentOpcode = opcodes[i];

                if (originalOpcode != currentOpcode) {
                    insns.set(i, insn.withOpcode(currentOpcode));
                }
            }
        } else {
            insns = performExpansion(opcodes);
        }
    }

    private ArrayList<DalvInsn> performExpansion(Dop[] opcodes) {
        int size = insns.size();
        ArrayList<DalvInsn> result = new ArrayList<DalvInsn>(size * 2);

        for (int i = 0; i < size; i++) {
            DalvInsn insn = insns.get(i);
            Dop originalOpcode = insn.getOpcode();
            Dop currentOpcode = opcodes[i];
            DalvInsn prefix;
            DalvInsn suffix;

            if (currentOpcode != null) {
                prefix = null;
                suffix = null;
            } else {
                currentOpcode = findExpandedOpcodeForInsn(insn);
                BitSet compatRegs =
                    currentOpcode.getFormat().compatibleRegs(insn);
                prefix = insn.expandedPrefix(compatRegs);
                suffix = insn.expandedSuffix(compatRegs);

                insn = insn.expandedVersion(compatRegs);
            }

            if (prefix != null) {
                result.add(prefix);
            }

            if (currentOpcode != originalOpcode) {
                insn = insn.withOpcode(currentOpcode);
            }
            result.add(insn);

            if (suffix != null) {
                result.add(suffix);
            }
        }

        return result;
    }

    private void assignAddressesAndFixBranches() {
        for (;;) {
            assignAddresses();
            if (!fixBranches()) {
                break;
            }
        }
    }

    private void assignAddresses() {
        int address = 0;
        int size = insns.size();

        for (int i = 0; i < size; i++) {
            DalvInsn insn = insns.get(i);
            insn.setAddress(address);
            address += insn.codeSize();
        }
    }

    private boolean fixBranches() {
        int size = insns.size();
        boolean anyFixed = false;

        for (int i = 0; i < size; i++) {
            DalvInsn insn = insns.get(i);
            if (!(insn instanceof TargetInsn)) {
                continue;
            }

            Dop opcode = insn.getOpcode();
            TargetInsn target = (TargetInsn) insn;

            if (opcode.getFormat().branchFits(target)) {
                continue;
            }

            if (opcode.getFamily() == Opcodes.GOTO) {
                opcode = findOpcodeForInsn(insn, opcode);
                if (opcode == null) {
                    throw new UnsupportedOperationException("method too long");
                }
                insns.set(i, insn.withOpcode(opcode));
            } else {
                CodeAddress newTarget;
                try {
                    newTarget = (CodeAddress) insns.get(i + 1);
                } catch (IndexOutOfBoundsException ex) {
                    throw new IllegalStateException(
                            "unpaired TargetInsn (dangling)");
                } catch (ClassCastException ex) {
                    throw new IllegalStateException("unpaired TargetInsn");
                }
                TargetInsn gotoInsn =
                    new TargetInsn(Dops.GOTO, target.getPosition(),
                            RegisterSpecList.EMPTY, target.getTarget());
                insns.set(i, gotoInsn);
                insns.add(i, target.withNewTargetAndReversed(newTarget));
                size++;
                i++;
            }

            anyFixed = true;
        }

        return anyFixed;
    }
}
