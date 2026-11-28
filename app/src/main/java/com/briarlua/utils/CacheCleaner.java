package com.briarlua.utils;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class CacheCleaner {

    private CacheCleaner() {
    }

    public static List<File> cacheDirs(Context ctx) {
        List<File> dirs = new ArrayList<>();
        if (ctx == null) return dirs;
        try {
            File cd = ctx.getCacheDir();
            if (cd != null) dirs.add(cd);
        } catch (Throwable ignored) {
        }
        try {
            File[] ecd = ctx.getExternalCacheDirs();
            if (ecd != null) {
                for (File f : ecd) {
                    if (f != null) dirs.add(f);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            dirs.add(ctx.getDir("dexfiles", Context.MODE_PRIVATE));
        } catch (Throwable ignored) {
        }
        return dirs;
    }

    public static long size(Context ctx) {
        long total = 0;
        for (File dir : cacheDirs(ctx)) {
            total += dirSize(dir);
        }
        return total;
    }

    public static long clearAll(Context ctx) {
        long freed = 0;
        for (File dir : cacheDirs(ctx)) {
            freed += clearDir(dir);
        }
        return freed;
    }

    public static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        File[] children = dir.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File f : children) {
            total += (f.isDirectory()) ? dirSize(f) : safeLength(f);
        }
        return total;
    }

    public static long clearDir(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        long freed = 0;
        for (File f : children) {
            freed += deleteTree(f);
        }
        return freed;
    }

    private static long deleteTree(File file) {
        if (file == null || !file.exists()) return 0;
        long bytes = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File f : children) {
                    bytes += deleteTree(f);
                }
            }
        }
        long len = safeLength(file);
        try {
            file.delete();
            bytes += len;
        } catch (Throwable ignored) {
        }
        return bytes;
    }

    private static long safeLength(File f) {
        try {
            return f.isFile() ? f.length() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
