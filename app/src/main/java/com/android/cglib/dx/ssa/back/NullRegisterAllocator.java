
package com.android.cglib.dx.ssa.back;

import com.android.cglib.dx.ssa.BasicRegisterMapper;
import com.android.cglib.dx.ssa.RegisterMapper;
import com.android.cglib.dx.ssa.SsaMethod;

public class NullRegisterAllocator extends RegisterAllocator {
    public NullRegisterAllocator(SsaMethod ssaMeth,
            InterferenceGraph interference) {
        super(ssaMeth, interference);
    }

    @Override
    public boolean wantsParamsMovedHigh() {
        return false;
    }

    @Override
    public RegisterMapper allocateRegisters() {
        int oldRegCount = ssaMeth.getRegCount();

        BasicRegisterMapper mapper = new BasicRegisterMapper(oldRegCount);

        for (int i = 0; i < oldRegCount; i++) {
            mapper.addMapping(i, i*2, 2);
        }

        return mapper;
    }
}
