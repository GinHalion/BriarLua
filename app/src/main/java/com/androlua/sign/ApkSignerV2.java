package com.androlua.sign;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSignerEngine;
import com.android.apksig.ApkVerifier;
import com.android.apksig.DefaultApkSignerEngine;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipSections;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ApkSignerV2 {

    private ApkSignerV2() {
    }

    public static void sign(String srcApk, String dstApk, String keyDir,
                            String keyName, int minSdk, int targetSdk) throws Exception {
        File src = new File(srcApk);
        File dst = new File(dstApk);
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        long stamp = System.currentTimeMillis();
        File v1signed = new File(parent, dst.getName() + "." + stamp + ".v1.tmp");
        File aligned = new File(parent, dst.getName() + "." + stamp + ".align.tmp");
        File signed = new File(parent, dst.getName() + "." + stamp + ".sign.tmp");

        sweepStale(parent, stamp);

        try {

            PrivateKey privateKey = loadPrivateKey(new File(keyDir, keyName + ".pk8"));
            X509Certificate cert = loadCertificate(new File(keyDir, keyName + ".x509.pem"));
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    keyName, privateKey, Collections.singletonList(cert)).build();

            new ApkSigner.Builder(Collections.singletonList(signerConfig))
                    .setInputApk(src)
                    .setOutputApk(v1signed)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(false)
                    .setV3SigningEnabled(false)
                    .setMinSdkVersion(Math.max(minSdk, 21))
                    .build()
                    .sign();

            ZipAligner.align(v1signed, aligned);

            signV2V3Surgical(aligned, signed, signerConfig, minSdk);

            verifySignedApk(signed, Math.max(minSdk, 21), Math.max(targetSdk, 21));
            checkArsStoredAligned(signed);

            if (!signed.renameTo(dst)) {
                if (dst.exists()) {
                    dst.delete();
                }
                copyFile(signed, dst);
                signed.delete();
            }

        } finally {
            if (v1signed.exists()
                    && !v1signed.getAbsolutePath().equals(dst.getAbsolutePath())) {
                v1signed.delete();
            }
            if (aligned.exists()
                    && !aligned.getAbsolutePath().equals(dst.getAbsolutePath())) {
                aligned.delete();
            }
            if (signed.exists()
                    && !signed.getAbsolutePath().equals(dst.getAbsolutePath())) {
                signed.delete();
            }
        }
    }

    private static void signV2V3Surgical(File in, File out,
            ApkSigner.SignerConfig config, int minSdk) throws Exception {
        List<DefaultApkSignerEngine.SignerConfig> engineConfigs = new ArrayList<>(1);
        engineConfigs.add(new DefaultApkSignerEngine.SignerConfig.Builder(
                config.getName(), config.getPrivateKey(), config.getCertificates()).build());

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(in, "r");
             java.io.FileInputStream fis = new java.io.FileInputStream(in);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {

            com.android.apksig.util.DataSource apk = DataSources.asDataSource(raf);
            ZipSections sections = ApkUtils.findZipSections(apk);
            long fileLen = in.length();
            long cdOff = sections.getZipCentralDirectoryOffset();
            long cdSize = sections.getZipCentralDirectorySizeBytes();
            long eocdOff = sections.getZipEndOfCentralDirectoryOffset();

            DefaultApkSignerEngine engine = new DefaultApkSignerEngine.Builder(
                    engineConfigs, Math.max(minSdk, 21))
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build();
            ApkSignerEngine.OutputApkSigningBlockRequest2 req = engine.outputZipSections2(
                    apk.slice(0, cdOff),
                    apk.slice(cdOff, cdSize),
                    apk.slice(eocdOff, fileLen - eocdOff));
            byte[] block = req.getApkSigningBlock();
            int pad = req.getPaddingSizeBeforeApkSigningBlock();

            byte[] eocdBytes = new byte[(int) (fileLen - eocdOff)];
            raf.seek(eocdOff);
            raf.readFully(eocdBytes);
            ByteBuffer eocdBuf = ByteBuffer.wrap(eocdBytes).order(ByteOrder.LITTLE_ENDIAN);
            ZipUtils.setZipEocdCentralDirectoryOffset(eocdBuf, cdOff + pad + block.length);

            req.done();
            engine.close();

            byte[] buf = new byte[65536];
            long left = cdOff;
            int n;
            while (left > 0) {
                int want = (int) Math.min(buf.length, left);
                n = fis.read(buf, 0, want);
                if (n < 0) throw new java.io.IOException("v2/v3 拼接: 输入流意外结束");
                fos.write(buf, 0, n);
                left -= n;
            }
            fos.write(new byte[pad]);
            fos.write(block);
            left = eocdOff - cdOff;
            while (left > 0) {
                int want = (int) Math.min(buf.length, left);
                n = fis.read(buf, 0, want);
                if (n < 0) throw new java.io.IOException("v2/v3 拼接: 输入流意外结束(CD)");
                fos.write(buf, 0, n);
                left -= n;
            }
            fos.write(eocdBytes);
        }
    }

    private static void sweepStale(File parent, long now) {
        if (parent == null) return;
        File[] fs = parent.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if ((n.endsWith(".v1.tmp") || n.endsWith(".align.tmp") || n.endsWith(".sign.tmp"))
                    && now - f.lastModified() > 3600000L) {
                f.delete();
            }
        }
    }

    private static void verifySignedApk(File apk, int minSdk, int targetSdk) throws Exception {
        ApkVerifier.Result r;
        r = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(minSdk)
                .setMaxCheckedPlatformVersion(targetSdk)
                .build()
                .verify();
        if (!r.isVerified()) {
            throw new Exception("签名自校验失败: " + formatIssues(r.getErrors()));
        }
        if (minSdk < 24 && !r.isVerifiedUsingV1Scheme()) {
            throw new Exception("自校验失败: v1 签名缺失（Android 7- 无法安装）");
        }
        if (targetSdk >= 30 && !r.isVerifiedUsingV2Scheme()) {
            throw new Exception("自校验失败: v2 签名缺失（Android 11+ 无法安装）");
        }
    }

    private static String formatIssues(Iterable<ApkVerifier.IssueWithParams> issues) {
        StringBuilder sb = new StringBuilder();
        for (ApkVerifier.IssueWithParams i : issues) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(i);
        }
        if (sb.length() == 0) sb.append("未知原因");
        return sb.toString();
    }

    private static void checkArsStoredAligned(File apk) throws IOException {
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
                throw new IOException("自校验失败: zip 无 EOCD（文件被截断？）");
            }
            int n = u16(tail, eocd + 10);
            long cdOff = u32(tail, eocd + 16);

            long pos = cdOff;
            byte[] cen = new byte[46];
            boolean found = false;
            for (int i = 0; i < n; i++) {
                raf.seek(pos);
                raf.readFully(cen);
                if (cen[0] != 'P' || cen[1] != 'K' || cen[2] != 1 || cen[3] != 2) {
                    throw new IOException("自校验失败: 中央目录记录损坏 @" + pos);
                }
                int nlen = u16(cen, 28);
                int elen = u16(cen, 30);
                long lfhOff = u32(cen, 42);
                byte[] nameB = new byte[nlen];
                raf.readFully(nameB);
                String name = new String(nameB, StandardCharsets.UTF_8);

                if ("resources.arsc".equals(name)) {
                    byte[] lfh = new byte[30];
                    raf.seek(lfhOff);
                    raf.readFully(lfh);
                    if (lfh[0] != 'P' || lfh[1] != 'K' || lfh[2] != 3 || lfh[3] != 4) {
                        throw new IOException("自校验失败: resources.arsc 本地头损坏");
                    }
                    int lmethod = u16(lfh, 8);
                    int lnlen = u16(lfh, 26);
                    int lelen = u16(lfh, 28);
                    long dataOff = lfhOff + 30 + lnlen + lelen;
                    if (lmethod != 0) {
                        throw new IOException("自校验失败: resources.arsc 仍被压缩（method="
                                + lmethod + "），Android 11+ 将拒绝安装");
                    }
                    if (dataOff % 4 != 0) {
                        throw new IOException("自校验失败: resources.arsc 数据偏移 "
                                + dataOff + " 未 4 字节对齐");
                    }
                    found = true;
                }
                pos += 46 + nlen + elen;
            }
            if (!found) {
                throw new IOException("自校验失败: 产物中找不到 resources.arsc");
            }
        } finally {
            raf.close();
        }
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xffL) | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16) | ((b[off + 3] & 0xffL) << 24);
    }

    private static PrivateKey loadPrivateKey(File f) throws Exception {
        byte[] der = readMaybePem(readAll(f));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static X509Certificate loadCertificate(File f) throws Exception {
        byte[] der = readMaybePem(readAll(f));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    private static byte[] readMaybePem(byte[] raw) {
        String s = new String(raw, StandardCharsets.US_ASCII);
        String t = s.trim();
        if (t.startsWith("-----")) {
            int b = s.indexOf("-----BEGIN");
            int e = s.indexOf("-----END");
            if (b >= 0 && e > b) {
                StringBuilder body = new StringBuilder();
                for (String line : s.substring(b, e).split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("-----")) {
                        body.append(line);
                    }
                }
                return base64Decode(body.toString());
            }
        }
        return raw;
    }

    private static byte[] base64Decode(String s) {
        int[] rev = new int[128];
        java.util.Arrays.fill(rev, -1);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < alphabet.length(); i++) {
            rev[alphabet.charAt(i)] = i;
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(s.length());
        int acc = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') break;
            if (c >= 128 || rev[c] < 0) continue;
            acc = (acc << 6) | rev[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                bos.write((acc >> bits) & 0xFF);
            }
        }
        return bos.toByteArray();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) f.length())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }
}
