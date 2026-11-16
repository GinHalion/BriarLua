
package com.android.cglib.dx.rop.code;

public interface TranslationAdvice {
    public boolean hasConstantOperation(Rop opcode,
                                        RegisterSpec sourceA, RegisterSpec sourceB);

    public boolean requiresSourcesInOrder(Rop opcode, RegisterSpecList sources);

    public int getMaxOptimalRegisterCount();
}
