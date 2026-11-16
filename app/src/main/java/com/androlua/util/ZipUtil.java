package com.androlua.util;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

public class ZipUtil {

    private static final Logger logger = Logger.getLogger(ZipUtil.class.getName());
    private static final byte[] BUFFER = new byte[4096];

    public static boolean zip(String sourceFilePath, String zipFilePath) {
        boolean result = false;
        File source = new File(sourceFilePath);
        if (!source.exists()) {
            logger.info(sourceFilePath + " doesn't exist.");
            return result;
        }
        if (!source.isDirectory()) {
            logger.info(sourceFilePath + " is not a directory.");
            return result;
        }
        File zipFile = new File(zipFilePath + "/" + source.getName() + ".zip");
        if (zipFile.exists()) {
            logger.info(zipFile.getName() + " is already exist.");
            return result;
        } else {
            if (!zipFile.getParentFile().exists()) {
                if (!zipFile.getParentFile().mkdirs()) {
                    logger.info("cann't create file " + zipFile.getName());
                    return result;
                }
            }
        }
        logger.info("creating zip file...");
        FileOutputStream dest = null;
        ZipOutputStream out = null;
        try {
            dest = new FileOutputStream(zipFile);
            CheckedOutputStream checksum = new CheckedOutputStream(dest, new Adler32());
            out = new ZipOutputStream(new BufferedOutputStream(checksum));
            out.setMethod(ZipOutputStream.DEFLATED);
            compress(source, out, source.getName());
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
        if (result) {
            logger.info("done.");
        } else {
            logger.info("fail.");
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
                int index = file.getAbsolutePath().indexOf(mainFileName);
                String entryName = file.getAbsolutePath().substring(index);
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
                    compress(f, out, mainFileName);
                }
            }
        }
    }

    public static boolean unzip(String zipPath, String destPath) {
        return unzip(new File(zipPath), destPath);
    }

    public static boolean unzip(File zipFile, String destPath) {
        boolean result = false;
        if (!zipFile.exists()) {
            logger.info(zipFile.getName() + " doesn't exist.");
            return result;
        }
        File target = new File(destPath);
        if (!target.exists()) {
            if (!target.mkdirs()) {
                logger.info("cann't create file " + target.getName());
                return result;
            }
        }
        ZipInputStream zis = null;
        logger.info("start unzip file ...");
        try {
            FileInputStream fis = new FileInputStream(zipFile);
            CheckedInputStream checksum = new CheckedInputStream(fis, new Adler32());
            zis = new ZipInputStream(new BufferedInputStream(checksum));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                int count;
                String entryName = entry.getName();

                String newEntryName = destPath + "/" + entryName;
                System.out.println(newEntryName);
                File temp = new File(newEntryName).getParentFile();
                if (!temp.exists()) {
                    if (!temp.mkdirs()) {
                        throw new RuntimeException("create file " + temp.getName() + " fail");
                    }
                }
                FileOutputStream fos = new FileOutputStream(newEntryName);
                BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER.length);
                while ((count = zis.read(BUFFER, 0, BUFFER.length)) != -1) {
                    dest.write(BUFFER, 0, count);
                }
                dest.flush();
                dest.close();
            }
            result = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zis != null) {
                try {
                    zis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (result) {
            logger.info("done.");
        } else {
            logger.info("fail.");
        }
        return result;
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        int bytesRead;
        while ((bytesRead = input.read(BUFFER)) != -1) {
            output.write(BUFFER, 0, bytesRead);
        }
    }

    public static void append(String zipFilePath, String appendFilePath) throws Exception {
        File src = new File(zipFilePath);
        File tmp = new File(zipFilePath + ".tmp_" + System.currentTimeMillis());
        ZipFile war = null;
        ZipOutputStream append = null;
        boolean ok = false;
        try {
            war = new ZipFile(src);
            append = new ZipOutputStream(new FileOutputStream(tmp));

            Enumeration<? extends ZipEntry> entries = war.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                append.putNextEntry(e);
                if (!e.isDirectory()) {
                    copy(war.getInputStream(e), append);
                }
                append.closeEntry();
            }
            ZipEntry e = new ZipEntry(appendFilePath);
            append.putNextEntry(e);
            copy(new FileInputStream(new File(appendFilePath)), append);
            append.closeEntry();
            ok = true;
        } finally {
            if (append != null) try { append.close(); } catch (Exception ignored) {}
            if (war != null) try { war.close(); } catch (Exception ignored) {}
        }
        if (ok) {
            if (!tmp.renameTo(src)) {
                src.delete();
                if (!tmp.renameTo(src)) throw new IllegalStateException("cannot replace: " + zipFilePath);
            }
        } else {
            tmp.delete();
        }
    }

}
