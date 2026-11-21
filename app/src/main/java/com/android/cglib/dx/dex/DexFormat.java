
package com.android.cglib.dx.dex;

public final class DexFormat {
    private DexFormat() {}

    public static final int API_CURRENT = 14;

    public static final int API_NO_EXTENDED_OPCODES = 13;

    public static final String DEX_IN_JAR_NAME = "classes.dex";

    public static final String MAGIC_PREFIX = "dex\n";

    public static final String MAGIC_SUFFIX = "\0";

    public static final String VERSION_CURRENT = "036";

    public static final String VERSION_FOR_API_13 = "035";

    public static final int ENDIAN_TAG = 0x12345678;

    public static int magicToApi(byte[] magic) {
        if (magic.length != 8) {
            return -1;
        }

        if ((magic[0] != 'd') || (magic[1] != 'e') || (magic[2] != 'x') || (magic[3] != '\n') ||
                (magic[7] != '\0')) {
            return -1;
        }

        String version = "" + ((char) magic[4]) + ((char) magic[5]) +((char) magic[6]);

        if (version.equals(VERSION_CURRENT)) {
            return API_CURRENT;
        } else if (version.equals(VERSION_FOR_API_13)) {
            return 13;
        }

        return -1;
    }

    public static String apiToMagic(int targetApiLevel) {
        String version;

        if (targetApiLevel >= API_CURRENT) {
            version = VERSION_CURRENT;
        } else {
            version = VERSION_FOR_API_13;
        }

        return MAGIC_PREFIX + version + MAGIC_SUFFIX;
    }
}
