package com.briarlua.utils;

import android.os.Handler;
import android.os.Looper;

import com.luajava.LuaObject;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AsyncShell {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ExecutorService POOL =
            Executors.newCachedThreadPool(
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "briar-asyncshell");
                            t.setDaemon(true);
                            return t;
                        }
                    });

    private AsyncShell() {}

    public static void exec(final String command, final LuaObject callback) {
        POOL.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        final String result = runShell(command);
                        MAIN.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (callback != null) callback.call(result);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                });
                    }
                });
    }

    public static void exec(final String command) {
        POOL.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        runShell(command);
                    }
                });
    }

    private static String runShell(String command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            process = pb.start();

            StringBuilder sb = new StringBuilder(1 << 14);
            char[] buf = new char[4096];
            Reader reader = new InputStreamReader(process.getInputStream());
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            reader.close();
            try {
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
