package com.briarlua.utils;

import android.os.Handler;
import android.os.Looper;

import com.luajava.LuaObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class AsyncFile {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024;

    private static final ExecutorService POOL =
            Executors.newCachedThreadPool(
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "briar-asyncfile");
                            t.setDaemon(true);
                            return t;
                        }
                    });

    private AsyncFile() {}

    public static void read(final String path, final int maxBytes, final LuaObject callback) {
        POOL.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        final String[] result = readFile(path, maxBytes);
                        MAIN.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (callback != null) {
                                                callback.call(result[0], result[1]);
                                            }
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                });
                    }
                });
    }

    public static void read(final String path, final LuaObject callback) {
        read(path, DEFAULT_MAX_BYTES, callback);
    }

    private static String[] readFile(String path, int maxBytes) {
        if (path == null) return new String[] {null, "path is nil"};
        try {
            File f = new File(path);
            if (!f.isFile()) return new String[] {null, "not a file: " + path};
            long len = f.length();
            if (maxBytes > 0 && len > maxBytes) {
                return new String[] {null, "file too large: " + len + " > " + maxBytes + " bytes"};
            }

            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(len > 0 ? (int) len : 8192);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
            } finally {
                try {
                    fis.close();
                } catch (Throwable ignored) {
                }
            }
            return new String[] {new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), null};
        } catch (Throwable t) {
            return new String[] {null, t == null ? "unknown" : String.valueOf(t)};
        }
    }
}
