package com.briarlua;

import android.content.Context;
import android.util.Log;

import java.io.File;

public final class NativeCrashHandler {

    private static final String TAG = "NativeCrashHandler";
    private static boolean sInstalled = false;

    static {
        System.loadLibrary("luajava");
    }

    private NativeCrashHandler() {
    }

    public static File getCrashDir(Context context) {
        return new File(context.getFilesDir(), "crash_native");
    }

    public static void install(Context context, String activityComponent) {
        if (sInstalled) return;
        try {
            File dir = getCrashDir(context);
            if (!dir.exists()) dir.mkdirs();
            init(dir.getAbsolutePath(), activityComponent);
            sInstalled = true;
            Log.i(TAG, "native crash handler installed: " + dir);
        } catch (Throwable t) {
            Log.e(TAG, "native crash handler install failed", t);
        }
    }

    private static native void init(String crashDir, String activityComponent);
}
