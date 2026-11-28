package com.androlua.sign;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ZipAligner {

    private ZipAligner() {
    }

    static volatile int sCountBiasForTest = 0;

    public static void align(File in, File out) throws IOException {
        alignOnce(in, out, 0);
        long measured = measureArsDataOffset(out);
        int residue = (int) (measured % 4);
        if (residue != 0) {
            int correction = (4 - residue) & 3;
            alignOnce(in, out, correction);
            long m2 = measureArsDataOffset(out);
            if (m2 % 4 != 0) {
                throw new IOException("对齐失败: 校正后 arsc 偏移 " + m2
                        + " 仍未 4 字节对齐（首遍实测 " + measured
                        + "，校正量 " + correction + "）");
            }
        }
    }

    static void alignOnce(File in, File out, int correction) throws IOException {
        byte[] buffer = new byte[65536];
        try (ZipFile zin = new ZipFile(in);
             CountingOutputStream cos = new CountingOutputStream(
                     new BufferedOutputStream(new FileOutputStream(out)));
             ZipOutputStream zos = new ZipOutputStream(cos)) {

            String comment = zin.getComment();
            if (comment != null && comment.length() > 0) {
                zos.setComment(comment);
            }

            Enumeration<? extends ZipEntry> entries = zin.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                boolean stored = (e.getMethod() == ZipEntry.STORED
                        || "resources.arsc".equals(e.getName())) && !e.isDirectory();

                ZipEntry ne = new ZipEntry(e.getName());
                if (stored) {
                    byte[] data = readAll(zin, e, buffer);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    ne.setMethod(ZipEntry.STORED);
                    ne.setSize(data.length);
                    ne.setCrc(crc.getValue());

                    long pos = cos.getCount() + sCountBiasForTest;
                    int nameLen = e.getName().getBytes(StandardCharsets.UTF_8).length;
                    int pad = (4 - (int) ((pos + 30 + nameLen + 6) % 4)) & 3;
                    pad = (pad + correction) & 3;
                    byte[] extra = new byte[6 + pad];
                    extra[0] = 0x35;
                    extra[1] = (byte) 0xd9;
                    extra[2] = (byte) (2 + pad);
                    extra[3] = 0;
                    extra[4] = 0x04;
                    ne.setExtra(extra);
                    zos.putNextEntry(ne);
                    zos.write(data);
                    zos.closeEntry();
                } else {
                    zos.putNextEntry(ne);
                    try (InputStream entryIn = zin.getInputStream(e)) {
                        if (!e.isDirectory()) {
                            int n;
                            while ((n = entryIn.read(buffer)) != -1) {
                                zos.write(buffer, 0, n);
                            }
                        }
                    }
                    zos.closeEntry();
                }
            }
            zos.finish();
            zos.flush();
        }
    }

    static long measureArsDataOffset(File apk) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(apk, "r");
        try {
            long len = raf.length();
            byte[] tail = new byte[(int) Math.min(len, 65557 + 22)];
            raf.seek(len - tail.length);
            raf.readFully(tail);

            int eocd = -1;
            for (int i = tail.length - 22; i >= 0; i--) {
                if (tail[i] == 'P' && tail[i + 1] == 'K'
                        && tail[i + 2] == 5 && tail[i + 3] == 6) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) {
                throw new IOException("实测失败: zip 无 EOCD（文件被截断？）");
            }
            int n = u16(tail, eocd + 10);
            long pos = u32(tail, eocd + 16);
            byte[] cen = new byte[46];
            for (int i = 0; i < n; i++) {
                raf.seek(pos);
                raf.readFully(cen);
                if (cen[0] != 'P' || cen[1] != 'K' || cen[2] != 1 || cen[3] != 2) {
                    throw new IOException("实测失败: 中央目录记录损坏 @" + pos);
                }
                int nlen = u16(cen, 28);
                int elen = u16(cen, 30);
                long lfhOff = u32(cen, 42);
                byte[] nameB = new byte[nlen];
                raf.readFully(nameB);
                if ("resources.arsc".equals(new String(nameB, StandardCharsets.UTF_8))) {
                    byte[] lfh = new byte[30];
                    raf.seek(lfhOff);
                    raf.readFully(lfh);
                    if (lfh[0] != 'P' || lfh[1] != 'K' || lfh[2] != 3 || lfh[3] != 4) {
                        throw new IOException("实测失败: resources.arsc 本地头损坏 @" + lfhOff);
                    }
                    int method = u16(lfh, 8);
                    int lnlen = u16(lfh, 26);
                    int lelen = u16(lfh, 28);
                    if (method != 0) {
                        throw new IOException("实测失败: resources.arsc 仍被压缩（method="
                                + method + "）");
                    }
                    return lfhOff + 30 + lnlen + lelen;
                }
                pos += 46 + nlen + elen;
            }
            throw new IOException("实测失败: 产物中找不到 resources.arsc");
        } finally {
            raf.close();
        }
    }

    private static byte[] readAll(ZipFile zf, ZipEntry e, byte[] buffer) throws IOException {
        if (e.isDirectory()) {
            return new byte[0];
        }
        try (InputStream in = zf.getInputStream(e);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                     (int) Math.max(64, e.getSize()))) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream o) {
            super(o);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        long getCount() {
            return count;
        }
    }
}
