package com.androlua;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final CrashHandler INSTANCE = new CrashHandler();
    public static final String TAG = "CrashHandler";

    private Context mContext;
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private final Map<String, String> infos = new LinkedHashMap<>();
    private final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        this.mContext = context.getApplicationContext();
        this.mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable th) {
        try {
            handleException(th);
        } catch (Throwable t) {
            Log.e(TAG, "handleException itself crashed", t);
        } finally {
            if (mDefaultHandler != null) {
                mDefaultHandler.uncaughtException(thread, th);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(1);
            }
        }
    }

    private boolean handleException(Throwable th) {
        if (th == null) return false;
        try {
            collectDeviceInfo(mContext);
        } catch (Throwable t) {
            Log.e(TAG, "collectDeviceInfo failed", t);
        }
        try {
            String fileName = saveCrashInfo2File(th);
            if (fileName != null) {
                Log.e(TAG, "crash log saved: " + fileName);
            }
        } catch (Throwable t) {
            Log.e(TAG, "saveCrashInfo2File failed", t);
        }
        return true;
    }

    public void collectDeviceInfo(Context context) {
        infos.clear();
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (packageInfo != null) {
                infos.put("versionName", packageInfo.versionName == null ? "null" : packageInfo.versionName);
                infos.put("versionCode", String.valueOf(packageInfo.versionCode));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "collect package info failed", e);
        }

        collectBuildFields("android.os.Build");
        collectBuildFields("android.os.Build$VERSION");
    }

    private void collectBuildFields(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value == null) continue;
                    if (value instanceof String[]) {
                        infos.put(field.getName(), Arrays.toString((String[]) value));
                    } else {
                        infos.put(field.getName(), value.toString());
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "read field " + field.getName() + " failed", t);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "load class " + className + " failed", t);
        }
    }

    private String saveCrashInfo2File(Throwable th) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            th.printStackTrace(pw);
            Throwable cause = th.getCause();
            int depth = 0;
            while (cause != null && depth < 10) {
                cause.printStackTrace(pw);
                cause = cause.getCause();
                depth++;
            }
        } finally {
            pw.close();
        }
        sb.append(sw.toString());

        String fileName = "crash-" + formatter.format(new Date())
                + "-" + System.currentTimeMillis() + ".log";

        File crashDir = getCrashDir();
        if (crashDir == null) {
            Log.e(TAG, "no writable crash dir");
            return null;
        }
        if (!crashDir.exists() && !crashDir.mkdirs()) {
            Log.e(TAG, "mkdirs failed: " + crashDir.getAbsolutePath());
            return null;
        }

        File crashFile = new File(crashDir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(crashFile);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
            Log.e("crash", sb.toString());
            return crashFile.getAbsolutePath();
        } catch (Throwable t) {
            Log.e(TAG, "write crash file failed: " + crashFile.getAbsolutePath(), t);
            return null;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private File getCrashDir() {
        if (mContext == null) return null;
        try {
            File dir = new File(mContext.getFilesDir(), "crash");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        } catch (Throwable t) {
            Log.e(TAG, "getFilesDir failed", t);
        }
        return null;
    }
}