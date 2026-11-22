
package com.android.cglib.dx.dex.file;

import com.android.cglib.dx.util.AnnotatedOutput;

public abstract class Item {
    public Item() {
    }

    public abstract ItemType itemType();

    public final String typeName() {
        return itemType().toHuman();
    }

    public abstract int writeSize();

    public abstract void addContents(DexFile file);

    public abstract void writeTo(DexFile file, AnnotatedOutput out);
}
