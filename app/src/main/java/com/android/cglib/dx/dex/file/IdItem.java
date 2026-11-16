
package com.android.cglib.dx.dex.file;

import com.android.cglib.dx.rop.cst.CstType;

public abstract class IdItem extends IndexedItem {
    private final CstType type;

    public IdItem(CstType type) {
        if (type == null) {
            throw new NullPointerException("type == null");
        }

        this.type = type;
    }

    @Override
    public void addContents(DexFile file) {
        TypeIdsSection typeIds = file.getTypeIds();
        typeIds.intern(type);
    }

    public final CstType getDefiningClass() {
        return type;
    }
}
