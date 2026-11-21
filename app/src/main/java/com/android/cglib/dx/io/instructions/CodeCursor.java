
package com.android.cglib.dx.io.instructions;

public interface CodeCursor {
    public int cursor();

    public int baseAddressForCursor();

    public void setBaseAddress(int targetAddress, int baseAddress);
}
