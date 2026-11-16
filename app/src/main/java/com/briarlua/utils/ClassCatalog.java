package com.briarlua.utils;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ClassCatalog {

    private static final String ASSET_PATH = "lua/utils/PublicClasses.lua";

    private static final String[] CATEGORIES = {
        "Android", "AndroidX", "AndroLua", "BriarLua", "Material", "Glide",
        "GitHub", "Java", "Kotlin", "Okhttp3", "Okio", "Other"
    };

    private static volatile String sPayload;
    private static volatile String sRaw;

    private ClassCatalog() {}

    public static void warmAsync(Context context) {
        if (sPayload != null) return;
        final Context app = context.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    getPayload(app);
                } catch (Throwable ignored) {
                }
            }
        }, "briar-classcatalog-warm");
        t.setDaemon(true);
        t.start();
    }

    public static String getPayload(Context context) {
        String p = sPayload;
        if (p != null) return p;
        synchronized (ClassCatalog.class) {
            if (sPayload == null) {
                try {
                    build(context.getApplicationContext());
                } catch (Throwable ignored) {
                    sPayload = "";
                    sRaw = "";
                }
            }
            return sPayload;
        }
    }

    public static String getRaw(Context context) {
        String r = sRaw;
        if (r != null) return r;
        synchronized (ClassCatalog.class) {
            if (sRaw == null) {
                try {
                    build(context.getApplicationContext());
                } catch (Throwable ignored) {
                    sPayload = "";
                    sRaw = "";
                }
            }
            return sRaw;
        }
    }

    private static void build(Context app) throws Exception {
        String src = readAsset(app, ASSET_PATH);
        int start = src.indexOf("[==[");
        int end = src.lastIndexOf("]==]");
        String body;
        if (start >= 0 && end > start) {
            body = src.substring(start + 4, end);
        } else {
            int start2 = src.indexOf('{');
            int end2 = src.lastIndexOf('}');
            if (start2 < 0 || end2 <= start2) {
                sPayload = "";
                sRaw = "";
                return;
            }
            body = src.substring(start2 + 1, end2).replace('"', '\n').replace(',', '\n');
        }

        StringBuilder raw = new StringBuilder(1 << 20);
        StringBuilder payload = new StringBuilder(1 << 20);
        int len = body.length();
        int lineStart = 0;
        while (lineStart <= len) {
            int nl = body.indexOf('\n', lineStart);
            if (nl < 0) nl = len;
            if (nl > lineStart) {
                String name = body.substring(lineStart, nl).trim();
                if (!name.isEmpty()) {
                    raw.append(name).append('\n');
                    payload.append("All\t").append(name).append('\n');
                    boolean any = false;
                    for (String cat : categoriesOf(name)) {
                        payload.append(cat).append('\t').append(name).append('\n');
                        any = true;
                    }
                    if (!any) {
                        payload.append("Other\t").append(name).append('\n');
                    }
                }
            }
            if (nl >= len) break;
            lineStart = nl + 1;
        }
        sRaw = raw.toString();
        sPayload = payload.toString();
    }

    private static String[] categoriesOf(String s) {
        String[] out = new String[CATEGORIES.length - 1];
        int n = 0;

        if (s.startsWith("android.")) out[n++] = "Android";
        if (s.startsWith("androidx.")) out[n++] = "AndroidX";
        if (s.startsWith("com.androlua.") || s.startsWith("com.luajava.")
                || s.startsWith("com.nirenr.")) {
            out[n++] = "AndroLua";
        }
        if (s.startsWith("com.briarlua.")) out[n++] = "BriarLua";
        if (s.startsWith("com.google.android.material.")) out[n++] = "Material";
        if (s.contains("glide")) out[n++] = "Glide";
        if (s.contains("github.")) out[n++] = "GitHub";
        if (s.startsWith("java.") || s.startsWith("javax.")) out[n++] = "Java";
        if (s.startsWith("kotlin.") || s.startsWith("kotlinx.")) out[n++] = "Kotlin";
        if (s.startsWith("okhttp3.")) out[n++] = "Okhttp3";
        if (s.startsWith("okio.")) out[n++] = "Okio";

        String[] result = new String[n];
        System.arraycopy(out, 0, result, 0, n);
        return result;
    }

    private static String readAsset(Context ctx, String path) throws Exception {
        InputStream is = ctx.getAssets().open(path);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            try {
                is.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
