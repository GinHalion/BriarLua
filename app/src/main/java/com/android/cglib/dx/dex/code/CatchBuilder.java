
package com.android.cglib.dx.dex.code;

import java.util.HashSet;

import com.android.cglib.dx.rop.type.Type;

public interface CatchBuilder {
    public CatchTable build();

    public boolean hasAnyCatches();

    public HashSet<Type> getCatchTypes();
}
