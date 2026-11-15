
package com.android.cglib.dx.rop.code;

import java.util.HashMap;

import com.android.cglib.dx.rop.type.TypeBearer;
import com.android.cglib.dx.util.MutabilityControl;

public final class LocalVariableInfo
        extends MutabilityControl {
    private final int regCount;

    private final RegisterSpecSet emptySet;

    private final RegisterSpecSet[] blockStarts;

    private final HashMap<Insn, RegisterSpec> insnAssignments;

    public LocalVariableInfo(RopMethod method) {
        if (method == null) {
            throw new NullPointerException("method == null");
        }

        BasicBlockList blocks = method.getBlocks();
        int maxLabel = blocks.getMaxLabel();

        this.regCount = blocks.getRegCount();
        this.emptySet = new RegisterSpecSet(regCount);
        this.blockStarts = new RegisterSpecSet[maxLabel];
        this.insnAssignments =
            new HashMap<Insn, RegisterSpec>(blocks.getInstructionCount());

        emptySet.setImmutable();
    }

    public void setStarts(int label, RegisterSpecSet specs) {
        throwIfImmutable();

        if (specs == null) {
            throw new NullPointerException("specs == null");
        }

        try {
            blockStarts[label] = specs;
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("bogus label");
        }
    }

    public boolean mergeStarts(int label, RegisterSpecSet specs) {
        RegisterSpecSet start = getStarts0(label);
        boolean changed = false;

        if (start == null) {
            setStarts(label, specs);
            return true;
        }

        RegisterSpecSet newStart = start.mutableCopy();
        if (start.size() != 0) {
            newStart.intersect(specs, true);
        } else {
            newStart = specs.mutableCopy();
        }

        if (start.equals(newStart)) {
            return false;
        }

        newStart.setImmutable();
        setStarts(label, newStart);

        return true;
    }

    public RegisterSpecSet getStarts(int label) {
        RegisterSpecSet result = getStarts0(label);

        return (result != null) ? result : emptySet;
    }

    public RegisterSpecSet getStarts(BasicBlock block) {
        return getStarts(block.getLabel());
    }

    public RegisterSpecSet mutableCopyOfStarts(int label) {
        RegisterSpecSet result = getStarts0(label);

        return (result != null) ?
            result.mutableCopy() : new RegisterSpecSet(regCount);
    }

    public void addAssignment(Insn insn, RegisterSpec spec) {
        throwIfImmutable();

        if (insn == null) {
            throw new NullPointerException("insn == null");
        }

        if (spec == null) {
            throw new NullPointerException("spec == null");
        }

        insnAssignments.put(insn, spec);
    }

    public RegisterSpec getAssignment(Insn insn) {
        return insnAssignments.get(insn);
    }

    public int getAssignmentCount() {
        return insnAssignments.size();
    }

    public void debugDump() {
        for (int label = 0 ; label < blockStarts.length; label++) {
            if (blockStarts[label] == null) {
                continue;
            }

            if (blockStarts[label] == emptySet) {
                System.out.printf("%04x: empty set\n", label);
            } else {
                System.out.printf("%04x: %s\n", label, blockStarts[label]);
            }
        }
    }

    private RegisterSpecSet getStarts0(int label) {
        try {
            return blockStarts[label];
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("bogus label");
        }
    }
}
