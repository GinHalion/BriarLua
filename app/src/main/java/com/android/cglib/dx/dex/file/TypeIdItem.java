
package com.android.cglib.dx.dex.file;

import com.android.cglib.dx.dex.SizeOf;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.cst.CstType;
import com.android.cglib.dx.util.AnnotatedOutput;
import com.android.cglib.dx.util.Hex;

public final class TypeIdItem extends IdItem {
    public TypeIdItem(CstType type) {
        super(type);
    }

    @Override
    public ItemType itemType() {
        return ItemType.TYPE_TYPE_ID_ITEM;
    }

    @Override
    public int writeSize() {
        return SizeOf.TYPE_ID_ITEM;
    }

    @Override
    public void addContents(DexFile file) {
        file.getStringIds().intern(getDefiningClass().getDescriptor());
    }

    @Override
    public void writeTo(DexFile file, AnnotatedOutput out) {
        CstType type = getDefiningClass();
        CstString descriptor = type.getDescriptor();
        int idx = file.getStringIds().indexOf(descriptor);

        if (out.annotates()) {
            out.annotate(0, indexString() + ' ' + descriptor.toHuman());
            out.annotate(4, "  descriptor_idx: " + Hex.u4(idx));
        }

        out.writeInt(idx);
    }
}
