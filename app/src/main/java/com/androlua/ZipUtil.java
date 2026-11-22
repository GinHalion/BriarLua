package com.androlua;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
    public static boolean zip(String sourceFilePath, String zipFilePath) {
        return LuaUtil.zip(sourceFilePath, zipFilePath);
    }

    public static boolean unzip(String zipPath, String destPath) {
        try {
            LuaUtil.unZip(zipPath, destPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void extractFileFromZip(String zipPath, String entryName, String outputPath)
            throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipPath);
            ZipEntry entry = zipFile.getEntry(entryName);

            if (entry == null) {
                List<String> names = new ArrayList<>();
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    names.add(entries.nextElement().getName());
                }
                throw new FileNotFoundException(
                        "在ZIP文件中找不到指定条目: "
                                + entryName
                                + "\n可用条目: "
                                + names.toString());
            }

            if (entry.isDirectory()) {
                throw new IOException("指定条目是目录，无法提取: " + entryName);
            }

            File outputFile = new File(outputPath);
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()
                    && !parent.isDirectory()) {
                throw new IOException("无法创建输出目录: " + parent.getAbsolutePath());
            }

            InputStream is = null;
            OutputStream os = null;
            try {
                is = zipFile.getInputStream(entry);
                os = new FileOutputStream(outputFile, false);
                copyStream(is, os);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ignored) {
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void addFileToZip(File zipFile, File fileToAdd, String entryName)
            throws IOException {
        File tempZip = File.createTempFile("tempzip", ".zip", zipFile.getParentFile());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip));
                ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().equals(entryName)) {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    if (!entry.isDirectory()) {
                        copyStream(zis, zos);
                    }
                    zos.closeEntry();
                }
                zis.closeEntry();
            }

            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(fileToAdd)) {
                copyStream(fis, zos);
            }
            zos.closeEntry();

        } catch (FileNotFoundException e) {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip));
                    FileInputStream fis = new FileInputStream(fileToAdd)) {
                zos.putNextEntry(new ZipEntry(entryName));
                copyStream(fis, zos);
                zos.closeEntry();
            }
        }

        if (!tempZip.renameTo(zipFile)) {
            try (InputStream in = new FileInputStream(tempZip);
                    OutputStream out = new FileOutputStream(zipFile)) {
                copyStream(in, out);
            }
            tempZip.delete();
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
    }
}
