
package com.android.cglib.dx.dex.file;

import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.util.AnnotatedOutput;
import com.android.cglib.dx.util.ByteArray;
import com.android.cglib.dx.util.Hex;
import com.android.cglib.dx.util.Leb128Utils;

public final class StringDataItem extends OffsettedItem {
    private final CstString value;

    public StringDataItem(CstString value) {
        super(1, writeSize(value));

        this.value = value;
    }

    private static int writeSize(CstString value) {
        int utf16Size = value.getUtf16Size();

        return Leb128Utils.unsignedLeb128Size(utf16Size)
            + value.getUtf8Size() + 1;
    }

    @Override
    public ItemType itemType() {
        return ItemType.TYPE_STRING_DATA_ITEM;
    }

    @Override
    public void addContents(DexFile file) {
    }

    @Override
    public void writeTo0(DexFile file, AnnotatedOutput out) {
        ByteArray bytes = value.getBytes();
        int utf16Size = value.getUtf16Size();

        if (out.annotates()) {
            out.annotate(Leb128Utils.unsignedLeb128Size(utf16Size),
                    "utf16_size: " + Hex.u4(utf16Size));
            out.annotate(bytes.size() + 1, value.toQuoted());
        }

        out.writeUleb128(utf16Size);
        out.write(bytes);
        out.writeByte(0);
    }

    @Override
    public String toHuman() {
        return value.toQuoted();
    }

    @Override
    protected int compareTo0(OffsettedItem other) {
        StringDataItem otherData = (StringDataItem) other;

        return value.compareTo(otherData.value);
    }
}
