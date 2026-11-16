
package com.android.cglib.dx.dex.file;

import java.util.Collection;
import java.util.TreeMap;

import com.android.cglib.dx.rop.cst.Constant;
import com.android.cglib.dx.rop.cst.CstNat;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.util.AnnotatedOutput;
import com.android.cglib.dx.util.Hex;

public final class StringIdsSection
        extends UniformItemSection {
    private final TreeMap<CstString, StringIdItem> strings;

    public StringIdsSection(DexFile file) {
        super("string_ids", file, 4);

        strings = new TreeMap<CstString, StringIdItem>();
    }

    @Override
    public Collection<? extends Item> items() {
        return strings.values();
    }

    @Override
    public IndexedItem get(Constant cst) {
        if (cst == null) {
            throw new NullPointerException("cst == null");
        }

        throwIfNotPrepared();

        IndexedItem result = strings.get((CstString) cst);

        if (result == null) {
            throw new IllegalArgumentException("not found");
        }

        return result;
    }

    public void writeHeaderPart(AnnotatedOutput out) {
        throwIfNotPrepared();

        int sz = strings.size();
        int offset = (sz == 0) ? 0 : getFileOffset();

        if (out.annotates()) {
            out.annotate(4, "string_ids_size: " + Hex.u4(sz));
            out.annotate(4, "string_ids_off:  " + Hex.u4(offset));
        }

        out.writeInt(sz);
        out.writeInt(offset);
    }

    public StringIdItem intern(String string) {
        return intern(new StringIdItem(new CstString(string)));
    }

    public StringIdItem intern(CstString string) {
        return intern(new StringIdItem(string));
    }

    public StringIdItem intern(StringIdItem string) {
        if (string == null) {
            throw new NullPointerException("string == null");
        }

        throwIfPrepared();

        CstString value = string.getValue();
        StringIdItem already = strings.get(value);

        if (already != null) {
            return already;
        }

        strings.put(value, string);
        return string;
    }

    public void intern(CstNat nat) {
        intern(nat.getName());
        intern(nat.getDescriptor());
    }

    public int indexOf(CstString string) {
        if (string == null) {
            throw new NullPointerException("string == null");
        }

        throwIfNotPrepared();

        StringIdItem s = strings.get(string);

        if (s == null) {
            throw new IllegalArgumentException("not found");
        }

        return s.getIndex();
    }

    @Override
    protected void orderItems() {
        int idx = 0;

        for (StringIdItem s : strings.values()) {
            s.setIndex(idx);
            idx++;
        }
    }
}
