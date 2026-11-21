
package com.android.cglib.dx.dex.file;

import com.android.cglib.dx.util.DexException;

public abstract class MemberIdsSection extends UniformItemSection {
    private static final int MAX_MEMBERS = 0x10000;

    public MemberIdsSection(String name, DexFile file) {
        super(name, file, 4);
    }

    @Override
    protected void orderItems() {
        int idx = 0;

        if (items().size() > MAX_MEMBERS) {
            String memberType = this instanceof MethodIdsSection ? "methods" : "fields";
            throw new DexException("Too many " + memberType + ": " + items().size()
                    + "; max is " + MAX_MEMBERS);
        }

        for (Object i : items()) {
            ((MemberIdItem) i).setIndex(idx);
            idx++;
        }
    }
}
