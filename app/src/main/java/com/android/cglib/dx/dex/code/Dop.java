
package com.android.cglib.dx.dex.code;

import com.android.cglib.dx.io.OpcodeInfo;
import com.android.cglib.dx.io.Opcodes;

public final class Dop {
    private final int opcode;

    private final int family;

    private final int nextOpcode;

    private final InsnFormat format;

    private final boolean hasResult;

    public Dop(int opcode, int family, int nextOpcode, InsnFormat format,
            boolean hasResult) {
        if (!Opcodes.isValidShape(opcode)) {
            throw new IllegalArgumentException("bogus opcode");
        }

        if (!Opcodes.isValidShape(family)) {
            throw new IllegalArgumentException("bogus family");
        }

        if (!Opcodes.isValidShape(nextOpcode)) {
            throw new IllegalArgumentException("bogus nextOpcode");
        }

        if (format == null) {
            throw new NullPointerException("format == null");
        }

        this.opcode = opcode;
        this.family = family;
        this.nextOpcode = nextOpcode;
        this.format = format;
        this.hasResult = hasResult;
    }

    @Override
    public String toString() {
        return getName();
    }

    public int getOpcode() {
        return opcode;
    }

    public int getFamily() {
        return family;
    }

    public InsnFormat getFormat() {
        return format;
    }

    public boolean hasResult() {
        return hasResult;
    }

    public String getName() {
        return OpcodeInfo.getName(opcode);
    }

    public int getNextOpcode() {
        return nextOpcode;
    }

    public Dop getOppositeTest() {
        switch (opcode) {
            case Opcodes.IF_EQ:  return Dops.IF_NE;
            case Opcodes.IF_NE:  return Dops.IF_EQ;
            case Opcodes.IF_LT:  return Dops.IF_GE;
            case Opcodes.IF_GE:  return Dops.IF_LT;
            case Opcodes.IF_GT:  return Dops.IF_LE;
            case Opcodes.IF_LE:  return Dops.IF_GT;
            case Opcodes.IF_EQZ: return Dops.IF_NEZ;
            case Opcodes.IF_NEZ: return Dops.IF_EQZ;
            case Opcodes.IF_LTZ: return Dops.IF_GEZ;
            case Opcodes.IF_GEZ: return Dops.IF_LTZ;
            case Opcodes.IF_GTZ: return Dops.IF_LEZ;
            case Opcodes.IF_LEZ: return Dops.IF_GTZ;
        }

        throw new IllegalArgumentException("bogus opcode: " + this);
    }
}
