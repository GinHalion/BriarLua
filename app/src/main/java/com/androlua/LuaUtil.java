package com.androlua;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.luajava.LuaException;
import com.luajava.LuaFunction;
import com.luajava.LuaString;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexFile;

import static java.io.File.separator;

public class LuaUtil {
    public static Bitmap captureScreen(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager WM = (WindowManager) activity
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = WM.getDefaultDisplay();
        display.getMetrics(metrics);
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;
        int pixelformat = display.getPixelFormat();
        PixelFormat localPixelFormat1 = new PixelFormat();
        PixelFormat.getPixelFormatInfo(pixelformat, localPixelFormat1);
        int deepth = localPixelFormat1.bytesPerPixel;
        byte[] piex = new byte[height * width * deepth];
        try {
            Runtime.getRuntime().exec(
                    new String[]{"/system/bin/su", "-c",
                            "chmod 777 /dev/graphics/fb0"});
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            InputStream stream = new FileInputStream(new File(
                    "/dev/graphics/fb0"));
            DataInputStream dStream = new DataInputStream(stream);
            dStream.readFully(piex);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int[] colors = new int[height * width];
        for (int m = 0; m < colors.length; m++) {
            int r = (piex[m * 4] & 0xFF);
            int g = (piex[m * 4 + 1] & 0xFF);
            int b = (piex[m * 4 + 2] & 0xFF);
            int a = (piex[m * 4 + 3] & 0xFF);
            colors[m] = (a << 24) + (r << 16) + (g << 8) + b;
        }
        Bitmap bitmap = Bitmap.createBitmap(colors, width, height,
                Bitmap.Config.ARGB_8888);
        return bitmap;
    }


    public static byte[] readAsset(Context context, String name) throws IOException {
        AssetManager am = context.getAssets();
        InputStream is = am.open(name);
        try {
            return readAll(is);
        } finally {
            is.close();
        }
    }

    public static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        byte[] ret = output.toByteArray();
        output.close();
        return ret;
    }

    public static void assetsToSD(Context context, String InFileName, String OutFileName) throws IOException {
        InputStream myInput;
        OutputStream myOutput = new FileOutputStream(OutFileName);
        myInput = context.getAssets().open(InFileName);
        byte[] buffer = new byte[8192];
        int length = myInput.read(buffer);
        while (length > 0) {
            myOutput.write(buffer, 0, length);
            length = myInput.read(buffer);
        }

        myOutput.flush();
        myInput.close();
        myOutput.close();
    }

    public static void copyFile(String from, String to) {
        try {
            copyFile(new FileInputStream(from), new FileOutputStream(to));
        } catch (IOException e) {
            Log.i("lua", e.getMessage());
        }
    }

    public static boolean copyFile(InputStream in, OutputStream out) {
        try {
            int byteread = 0;
            byte[] buffer = new byte[8192];
            while ((byteread = in.read(buffer)) != -1) {
                out.write(buffer, 0, byteread);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            Log.i("lua", "" + e.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    public static boolean copyDir(String from, String to) {
        return copyDir(new File(from), new File(to));
    }

    public static boolean copyDir(File from, File to) {
        boolean ret = true;
        File p = to.getParentFile();
        if (!p.exists())
            p.mkdirs();
        if (from.isDirectory()) {
            File[] fs = from.listFiles();
            if (fs != null && fs.length != 0) {
                for (File f : fs)
                    ret = copyDir(f, new File(to, f.getName()));
            } else {
                if (!to.exists())
                    ret = to.mkdirs();
            }
        } else {
            try {
                if (!to.exists())
                    to.createNewFile();
                ret = copyFile(new FileInputStream(from), new FileOutputStream(to));
            } catch (IOException e) {
                Log.i("lua", e.getMessage());
                ret = false;
            }
        }
        return ret;
    }

    public static boolean rmDir(File dir) {
        if (dir.isDirectory()) {
            File[] fs = dir.listFiles();
            for (File f : fs)
                rmDir(f);
        }
        return dir.delete();
    }

    public static void rmDir(File dir, String ext) {
        if (dir.isDirectory()) {
            File[] fs = dir.listFiles();
            for (File f : fs)
                rmDir(f, ext);
            dir.delete();
        }
        if (dir.getName().endsWith(ext))
            dir.delete();
    }

    public static byte[] readZip(String zippath, String filepath) throws IOException {
        ZipFile zip = new ZipFile(zippath);
        try {
            ZipEntry entey = zip.getEntry(filepath);
            if (entey == null) return null;
            InputStream is = zip.getInputStream(entey);
            try {
                return readAll(is);
            } finally {
                is.close();
            }
        } finally {
            zip.close();
        }
    }


    public static String getFileMD5(String file) {
        return getFileMD5(new File(file));
    }

    public static String getFileMD5(File file) {
        try {
            return getFileMD5(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static String getFileMD5(InputStream in) {
        byte buffer[] = new byte[8192];
        int len;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static String getFileSha1(String file) {
        return getFileMD5(new File(file));
    }

    public static String getFileSha1(File file) {
        try {
            return getFileSha1(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static String getFileSha1(InputStream in) {
        byte buffer[] = new byte[8192];
        int len;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            while ((len = in.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String getMessageDigest(String in,String algorithm) {
        byte[] buffer = in.getBytes();
        int len = buffer.length;
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(buffer, 0, len);
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getMD5(String in) {
        byte[] buffer = in.getBytes();
        int len = buffer.length;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(buffer, 0, len);
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getSha1(String in) {
        byte[] buffer = in.getBytes();
        int len = buffer.length;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(buffer, 0, len);
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String[] getAllName(Context context, String path) {
        ArrayList<String> ret = new ArrayList<>();
        DexFile dex = null;
        try {
            dex = new DexFile(context.getPackageCodePath());
            Enumeration<String> cls = dex.entries();
            while (cls.hasMoreElements()) {
                ret.add(cls.nextElement());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { if (dex != null) dex.close(); } catch (Exception ignored) {}
        }
        ZipFile zip = null;
        try {
            zip = new ZipFile(path);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ret.add(entries.nextElement().getName().replaceAll("/", ".").replace(".class", ""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (zip != null) zip.close(); } catch (Exception ignored) {}
        }

        String[] arr = new String[ret.size()];
        ret.toArray(arr);
        return arr;
    }


    public static void unZip(String SourceDir) throws IOException {
        unZip(SourceDir, new File(SourceDir).getParent(), "");
    }

    public static void unZip(String SourceDir, boolean bool) throws IOException {
        if (!bool) {
            unZip(SourceDir);
            return;
        }
        String name = new File(SourceDir).getName();
        int i = name.lastIndexOf(".");
        if (i > 0) {
            name = name.substring(0, i);
        }
        i = name.indexOf("_");
        if (i > 0) {
            name = name.substring(0, i);
        }
        i = name.indexOf("(");
        if (i > 0) {
            name = name.substring(0, i);
        }
        unZip(SourceDir, new File(SourceDir).getParent() + separator + name, "");
    }

    public static void unZip(String SourceDir, String extDir) throws IOException {
        unZip(SourceDir, extDir, "");
    }

    public static void unZip(String SourceDir, String extDir, String fileExt) throws IOException {
        ZipFile zip = new ZipFile(SourceDir);
        try {
            File extDirFile = new File(extDir).getCanonicalFile();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(fileExt))
                    continue;
                String path = name;
                if (entry.isDirectory()) {
                    File f = new File(extDir + separator + path);
                    if (!f.exists())
                        f.mkdirs();
                } else {
                    File outF = new File(extDir + separator + path);
                    String cpath = outF.getCanonicalPath();
                    if (!cpath.equals(extDirFile.getPath())
                            && !cpath.startsWith(extDirFile.getPath() + separator)) {
                        throw new IOException("blocked zip entry (zip-slip): " + name);
                    }
                    File temp = outF.getParentFile();
                    if (!temp.exists()) {
                        if (!temp.mkdirs()) {
                            throw new RuntimeException("create file " + temp.getName() + " fail");
                        }
                    }

                    FileOutputStream out = new FileOutputStream(outF);
                    InputStream in = zip.getInputStream(entry);
                    try {
                        byte[] buf = new byte[8192];
                        int count = 0;
                        while ((count = in.read(buf)) != -1) {
                            out.write(buf, 0, count);
                        }
                    } finally {
                        out.close();
                        in.close();
                    }
                }
            }
        } finally {
            zip.close();
        }
    }


    private static final byte[] BUFFER = new byte[8192];

    public static boolean zip(String sourceFilePath) {
        return zip(sourceFilePath, new File(sourceFilePath).getParent());
    }

    public static boolean zip(String sourceFilePath, String zipFilePath) {
        File f = new File(sourceFilePath);
        return zip(sourceFilePath, zipFilePath, f.getName() + ".zip");
    }

    public static boolean zip(String sourceFilePath, String zipFilePath, String zipFileName) {
        boolean result = false;
        File source = new File(sourceFilePath);
        File zipFile = new File(zipFilePath, zipFileName);
        if (!zipFile.getParentFile().exists()) {
            if (!zipFile.getParentFile().mkdirs()) {
                return result;
            }
        }
        if (zipFile.exists()) {
            try {
                zipFile.createNewFile();
            } catch (IOException e) {
                return result;
            }
        }

        FileOutputStream dest = null;
        ZipOutputStream out = null;
        try {
            dest = new FileOutputStream(zipFile);
            CheckedOutputStream checksum = new CheckedOutputStream(dest, new Adler32());
            out = new ZipOutputStream(new BufferedOutputStream(checksum));
            compress(source, out, "");
            checksum.getChecksum().getValue();
            result = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private static void compress(File file, ZipOutputStream out, String mainFileName) {
        if (file.isFile()) {
            FileInputStream fi = null;
            BufferedInputStream origin = null;
            try {
                fi = new FileInputStream(file);
                origin = new BufferedInputStream(fi, BUFFER.length);
                String entryName = mainFileName + file.getName();
                System.out.println(entryName);
                ZipEntry entry = new ZipEntry(entryName);
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(BUFFER, 0, BUFFER.length)) != -1) {
                    out.write(BUFFER, 0, count);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (origin != null) {
                    try {
                        origin.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (file.isDirectory()) {
            File[] fs = file.listFiles();
            if (fs != null && fs.length > 0) {
                for (File f : fs) {
                    if (f.isFile())
                        compress(f, out, mainFileName);
                    else
                        compress(f, out, mainFileName + f.getName() + "/");

                }
            }
        }
    }

    public static final HashMap<String, String> mFileTypes = new HashMap<String, String>();

    static {
        mFileTypes.put("FFD8FF", "jpg");
        mFileTypes.put("89504E47", "png");
        mFileTypes.put("47494638", "gif");
        mFileTypes.put("49492A00", "tif");
        mFileTypes.put("424D", "bmp");
        mFileTypes.put("41433130", "dwg");
        mFileTypes.put("38425053", "psd");
        mFileTypes.put("7B5C727466", "rtf");
        mFileTypes.put("3C3F786D6C", "xml");
        mFileTypes.put("68746D6C3E", "html");
        mFileTypes.put("44656C69766572792D646174653A", "eml");
        mFileTypes.put("D0CF11E0", "doc");
        mFileTypes.put("5374616E64617264204A", "mdb");
        mFileTypes.put("252150532D41646F6265", "ps");
        mFileTypes.put("255044462D312E", "pdf");
        mFileTypes.put("504B0304", "docx");
        mFileTypes.put("52617221", "rar");
        mFileTypes.put("57415645", "wav");
        mFileTypes.put("41564920", "avi");
        mFileTypes.put("2E524D46", "rm");
        mFileTypes.put("000001BA", "mpg");
        mFileTypes.put("000001B3", "mpg");
        mFileTypes.put("6D6F6F76", "mov");
        mFileTypes.put("3026B2758E66CF11", "asf");
        mFileTypes.put("4D546864", "mid");
        mFileTypes.put("1F8B08", "gz");
    }

    public static String getFileType(String path) {
        try {
            return mFileTypes.get(getFileHeader(new FileInputStream(path)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return "unknown";
    }


    public static String getFileType(File file) {
        try {
            return mFileTypes.get(getFileHeader(new FileInputStream(file)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return "unknown";
    }

    public static String getFileType(InputStream inputStream) {
        return mFileTypes.get(getFileHeader(inputStream));
    }

    public static String getFileHeader(InputStream inputStream) {
        String value = null;
        try {
            byte[] b = new byte[4];
            inputStream.read(b, 0, b.length);
            value = bytesToHexString(b);
        } catch (Exception e) {
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
        return value;
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder builder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        String hv;
        for (int i = 0; i < src.length; i++) {
            hv = Integer.toHexString(src[i] & 0xFF).toUpperCase();
            if (hv.length() < 2) {
                builder.append(0);
            }
            builder.append(hv);
        }
        return builder.toString();
    }

    public BitmapDrawable toBlack(String path, float n, int h, int o) {
        Bitmap image = BitmapFactory.decodeFile(path);
        int width = image.getWidth();
        int height = image.getHeight();
        Bitmap imageRet = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        int[] colors = new int[width * height];
        float[] vs = new float[width * height];
        float[] hsv = new float[3];
        float v = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color1 = image.getPixel(x, y);
                Color.colorToHSV(color1, hsv);
                vs[x + width * y] = hsv[2];
                v += hsv[2];
            }
        }
        float vv = v / (width * height) * n;
        int[][] color = new int[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = x + width * y;
                if (vs[i] > vv) {
                    colors[i] = -1;
                    color[x][y] = 1;
                } else {
                    colors[i] = 0xff000000;
                    color[x][y] = 0;
                }
            }
        }
        int ret = 0;
        for (int x = width / 2; x < width - 10; x++) {
            for (int y = width / 3; y < width; y++) {
                if (check(x, y, color, h, o)) {
                    ret = x;
                    Log.i("find_color", ret + "");
                    break;
                }
            }

        }
        return new BitmapDrawable(Bitmap.createBitmap(colors, width, height, Bitmap.Config.RGB_565));
    }

    private boolean check(int x, int y, int[][] color, int h, int o) {
        for (int i = 0; i < h; i++) {
            if (!(color[x][y + i] == 1 && color[x + o][y + i] == 0)) {
                return false;
            }
        }
        return true;
    }

    public int getDifferenceValue(String path1, String path2) {
        int result = 0;
        File file = new File(path1);
        File file1 = new File(path2);
        try {
            Bitmap image = BitmapFactory.decodeFile(path1);
            Bitmap image1 = BitmapFactory.decodeFile(path2);

            int width = image.getWidth();
            int height = image.getHeight();
            int[][] colors = new int[width][height];
            for (int x = 1; x < width; x++) {
                for (int y = 1; y < height; y++) {
                    int color1 = image.getPixel(x, y);
                    int color2 = image1.getPixel(x, y);
                    if (color1 == color2) {
                        colors[x - 1][y - 1] = 0;
                    } else {
                        colors[x - 1][y - 1] = 1;
                    }
                }
            }
            int min = 999;
            int max = -1;
            for (int x = 0; x < colors.length; x++) {
                for (int y = 0; y < colors[x].length; y++) {
                    if (colors[x][y] == 1) {
                        colors[x][y] = checkPixel(x, y, colors);
                        if (colors[x][y] == 1) {
                            if (x > max) {
                                max = x;
                            } else if (x < min) {
                                min = x;
                            }
                        }
                    }
                }
            }
            result = (max + min) / 2;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public int checkPixel(int x, int y, int[][] colors) {
        int result = colors[x][y];
        int num = 0;
        if ((y + 30) < colors[x].length) {
            for (int i = 1; i <= 30; i++) {
                int color = colors[x][y + i];
                if (color == 0) {
                    num += 1;
                }
            }
            if (num > 15) {
                return 0;
            }
        }
        return result;
    }

    public static Object dump(LuaFunction func) {
        try {
            byte[] bytes = func.dump();
            int w = (int) Math.sqrt(bytes.length);
            int h = w + 1;
            int[] ints = new int[w * h];
            int len = bytes.length / 4;
            for (int i = 0; i < len; i++) {
                int l = i * 4;
                ints[i] = Color.argb(bytes[l], bytes[l + 1], bytes[l + 2], bytes[l + 3]);
            }
            Bitmap bmp = Bitmap.createBitmap(ints, w, h, Bitmap.Config.ARGB_8888);
            FileOutputStream out = new FileOutputStream("/sdcard/a.png");
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return e;
        }
    }


    private static int compare(String str, String target) {
        int d[][];
        int n = str.length();
        int m = target.length();
        int i;
        int j;
        char ch1;
        char ch2;
        int temp;
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        d = new int[n + 1][m + 1];
        for (i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (i = 1; i <= n; i++) {
            ch1 = str.charAt(i - 1);
            for (j = 1; j <= m; j++) {
                ch2 = target.charAt(j - 1);
                if (ch1 == ch2 || ch1 == ch2 + 32 || ch1 + 32 == ch2) {
                    temp = 0;
                } else {
                    temp = 1;
                }
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + temp);
            }
        }
        return d[n][m];
    }


    private static int min(int one, int two, int three) {
        return (one = one < two ? one : two) < three ? one : three;
    }

    public static float getSimilarityRatio(String str, String target) {
        int max = Math.max(str.length(), target.length());
        return 1 - (float) compare(str, target) / max;
    }

    public static LuaString readZipFile(String zippath, String filepath) throws IOException {
        ZipFile zip = new ZipFile(zippath);
        try {
            ZipEntry entey = zip.getEntry(filepath);
            if (entey == null) return null;
            InputStream is = zip.getInputStream(entey);
            try {
                return new LuaString(readAll(is));
            } finally {
                is.close();
            }
        } finally {
            zip.close();
        }
    }

    public static LuaString readApkFile(String filepath) throws IOException {
        ZipFile zip = new ZipFile(LuaApplication.getInstance().getPackageCodePath());
        try {
            ZipEntry entey = zip.getEntry(filepath);
            if (entey == null) return null;
            InputStream is = zip.getInputStream(entey);
            try {
                return new LuaString(readAll(is));
            } finally {
                is.close();
            }
        } finally {
            zip.close();
        }
    }

    public static boolean isFileInZip(String zipFilePath, String targetFileName) {
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            String normalizedTarget = targetFileName.replace("\\", "/");
            
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().replace("\\", "/").equals(normalizedTarget)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void save2(String path, String text) {
        try {
            FileOutputStream out = new FileOutputStream(path);
            out.write(text.getBytes());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
