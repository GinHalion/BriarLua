
package com.android.cglib.dx.dex;

public class DexOptions {
    public int targetApiLevel = DexFormat.API_NO_EXTENDED_OPCODES;

    public String getMagic() {
        return DexFormat.apiToMagic(targetApiLevel);
    }

    public boolean canUseExtendedOpcodes() {
        return targetApiLevel >= DexFormat.API_CURRENT;
    }
}
