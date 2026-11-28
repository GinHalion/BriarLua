package com.androlua;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.activity.ComponentActivity;
import androidx.core.view.WindowCompat;

import com.luajava.LuaFunction;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SplashWelcome extends ComponentActivity {

    private static final String TAG = "SplashWelcome";

    private boolean isUpdata;
    private LuaApplication app;
    private String luaMdDir;
    private String localDir;
    private long mLastTime;
    private long mOldLastTime;
    private boolean isVersionChanged;
    private String mVersionName = "";
    private String mOldVersionName = "";

    private ZipFile zipFile;
    private String destPath;

    private volatile boolean updateSucceeded = true;
    private volatile boolean cancelled = false;

    private ExecutorService updateExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        app = (LuaApplication) getApplication();
        luaMdDir = app.luaMdDir;
        localDir = app.localDir;

        if (checkInfo()) {
            checkAndStartUpdate();
        } else {
            startActivity();
        }
    }

    private void checkAndStartUpdate() {
        updateExecutor = Executors.newSingleThreadExecutor();
        final Handler handler = new Handler(Looper.getMainLooper());
        updateExecutor.execute(() -> {
            onUpdate();
            handler.post(() -> {
                if (cancelled) return;
                if (!updateSucceeded) {
                    Log.e(TAG, "update failed, abort entering Main");
                    sendMsg();
                    finish();
                    return;
                }
                startActivity();
            });
        });
        updateExecutor.shutdown();
    }

    public void startActivity() {
        startMainActivity();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, Main.class);
        if (isVersionChanged) {
            intent.putExtra("isVersionChanged", isVersionChanged);
            intent.putExtra("newVersionName", mVersionName);
            intent.putExtra("oldVersionName", mOldVersionName);
        }
        startActivity(intent);
        finish();
    }

    private boolean checkInfo() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long lastTime = packageInfo.lastUpdateTime;
            String versionName = packageInfo.versionName != null ? packageInfo.versionName : "";
            SharedPreferences info = getSharedPreferences("appInfo", 0);
            String oldVersionName = info.getString("versionName", "");
            if (oldVersionName == null) oldVersionName = "";

            if (!versionName.equals(oldVersionName)) {
                SharedPreferences.Editor editor = info.edit();
                editor.putString("versionName", versionName);
                editor.apply();
                isVersionChanged = true;
                mVersionName = versionName;
                mOldVersionName = oldVersionName;
            }

            long oldLastTime = info.getLong("lastUpdateTime", 0);
            if (oldLastTime != lastTime) {
                SharedPreferences.Editor editor = info.edit();
                editor.putLong("lastUpdateTime", lastTime);
                editor.apply();
                isUpdata = true;
                mLastTime = lastTime;
                mOldLastTime = oldLastTime;
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        File mainFile = new File(app.getLuaPath("main.lua"));
        return !(mainFile.exists() && mainFile.isFile());
    }

    private void onUpdate() {
        LuaState L = LuaStateFactory.newLuaState();
        L.openLibs();
        try {
            if (L.LloadBuffer(LuaUtil.readAsset(this, "update.lua"), "update") == 0) {
                if (L.pcall(0, 0, 0) == 0) {
                    LuaFunction func = L.getFunction("onUpdate");
                    if (func != null) func.call(mVersionName, mOldVersionName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onUpdate lua callback failed", e);
            sendMsg();
        }

        try {
            boolean ok1 = unApk("assets", localDir);
            boolean ok2 = unApk("lua", luaMdDir);
            if (!ok1 || !ok2) updateSucceeded = false;
            cleanupLegacyFiles();
        } catch (IOException e) {
            Log.e(TAG, "unApk failed", e);
            updateSucceeded = false;
            sendMsg();
        }
    }

    private void cleanupLegacyFiles() {
        String[][] stale = {
                {localDir, "libs/mao.dex"},
                {localDir, "libs/sign.dex"},
                {localDir, "libs/apkeditor.dex"},
                {localDir, "libs/apkeditor.jar"},
                {localDir, "keys/platform.pk8"},
                {localDir, "keys/platform.x509.pem"},
                {localDir, "keys/platform.sbt"},
                {localDir, "keys/testkey.sbt"},
                {localDir, "keys/keystore.ks"},
        };
        for (String[] p : stale) {
            File f = new File(p[0], p[1]);
            if (f.exists() && f.isFile()) {
                if (!f.delete()) {
                    Log.w(TAG, "cannot delete legacy file: " + f.getAbsolutePath());
                }
            }
        }
        File libsDir = new File(localDir, "libs");
        File[] rest = libsDir.listFiles();
        if (libsDir.isDirectory() && rest != null && rest.length == 0) {
            libsDir.delete();
        }
    }

    private void sendMsg() {
    }

    public boolean unApk(String dir, String extDir) throws IOException {
        if (cancelled) return false;

        final ArrayList<String> dirtest = new ArrayList<>();
        final ExecutorService threadPool =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        final int i = dir.length() + 1;
        destPath = extDir;

        final ZipFile localZipFile = new ZipFile(getApplicationInfo().publicSourceDir);
        zipFile = localZipFile;

        try {
            final ArrayList<ZipEntry> inzipfile = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = localZipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String name = zipEntry.getName();
                if (!name.startsWith(dir)) continue;

                if (name.length() <= dir.length() || name.charAt(dir.length()) != '/') {
                    continue;
                }

                String path = name.substring(i);
                String fp = extDir + File.separator + path;

                if (!zipEntry.isDirectory()) {
                    inzipfile.add(zipEntry);
                    dirtest.add(fp + File.separator);
                    continue;
                }
                File file = new File(fp);
                if (!file.exists()) {
                    file.mkdirs();
                }
            }

            for (ZipEntry zipEntry : inzipfile) {
                if (cancelled) break;
                String path = zipEntry.getName().substring(i);
                String fp = extDir + File.separator + path;

                boolean skip = false;
                for (String d : dirtest) {
                    if (fp.startsWith(d)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;

                threadPool.execute(new FileWritingTask(localZipFile, zipEntry, path, extDir));
            }

            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                    updateSucceeded = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "unApk interrupted", e);
                updateSucceeded = false;
            }

            return updateSucceeded;
        } finally {
            try {
                localZipFile.close();
            } catch (IOException e) {
                Log.w(TAG, "close zip failed", e);
            }
            if (zipFile == localZipFile) zipFile = null;
        }
    }

    private class FileWritingTask implements Runnable {
        private final ZipFile localZipFile;
        private final ZipEntry zipEntry;
        private final String path;
        private final String extDir;

        FileWritingTask(ZipFile localZipFile, ZipEntry zipEntry, String path, String extDir) {
            this.localZipFile = localZipFile;
            this.zipEntry = zipEntry;
            this.path = path;
            this.extDir = extDir;
        }

        @Override
        public void run() {
            if (cancelled) return;

            File file = new File(extDir + File.separator + path);
            try {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        LuaUtil.rmDir(file);
                    } else {
                        file.delete();
                    }
                }
                if (file.exists()) {
                    Log.w(TAG, "Cannot delete existing file: " + file.getAbsolutePath());
                    updateSucceeded = false;
                    return;
                }

                File parentFile = file.getParentFile();
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs();
                }
                if (parentFile == null || !parentFile.isDirectory()) {
                    Log.e(TAG, "Invalid parent directory: "
                            + (parentFile == null ? "null" : parentFile.getAbsolutePath()));
                    updateSucceeded = false;
                    return;
                }

                if (cancelled) return;

                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    inputStream = localZipFile.getInputStream(zipEntry);
                    outputStream = new FileOutputStream(file);
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = inputStream.read(buffer)) >= 0) {
                        if (cancelled) {
                            updateSucceeded = false;
                            return;
                        }
                        outputStream.write(buffer, 0, n);
                    }
                    outputStream.flush();
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException ignore) {
                        }
                    }
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract: " + file.getAbsolutePath(), e);
                updateSucceeded = false;
            } catch (IllegalStateException e) {
                Log.w(TAG, "Zip closed while writing: " + file.getAbsolutePath(), e);
                updateSucceeded = false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
            updateExecutor = null;
        }
        ZipFile zf = zipFile;
        if (zf != null) {
            try {
                zf.close();
            } catch (IOException ignore) {
            }
            zipFile = null;
        }
        super.onDestroy();
    }
}