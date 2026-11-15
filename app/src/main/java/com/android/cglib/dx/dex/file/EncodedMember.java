
package com.android.cglib.dx.dex.file;

import java.io.PrintWriter;

import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.util.AnnotatedOutput;
import com.android.cglib.dx.util.ToHuman;

public abstract class EncodedMember implements ToHuman {
    private final int accessFlags;

    public EncodedMember(int accessFlags) {
        this.accessFlags = accessFlags;
    }

    public final int getAccessFlags() {
        return accessFlags;
    }

    public abstract CstString getName();

    public abstract void debugPrint(PrintWriter out, boolean verbose);

    public abstract void addContents(DexFile file);

    public abstract int encode(DexFile file, AnnotatedOutput out,
            int lastIndex, int dumpSeq);
}
