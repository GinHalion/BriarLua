
package com.android.cglib.dx.io.instructions;


public abstract class BaseCodeCursor implements CodeCursor {
    private final AddressMap baseAddressMap;

    private int cursor;

    public BaseCodeCursor() {
        this.baseAddressMap = new AddressMap();
        this.cursor = 0;
    }

    public final int cursor() {
        return cursor;
    }

    public final int baseAddressForCursor() {
        int mapped = baseAddressMap.get(cursor);
        return (mapped >= 0) ? mapped : cursor;
    }

    public final void setBaseAddress(int targetAddress, int baseAddress) {
        baseAddressMap.put(targetAddress, baseAddress);
    }

    protected final void advance(int amount) {
        cursor += amount;
    }
}
