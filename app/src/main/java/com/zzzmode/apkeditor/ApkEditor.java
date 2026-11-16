package com.zzzmode.apkeditor;

import com.zzzmode.apkeditor.axmleditor.editor.*;
import com.zzzmode.apkeditor.axmleditor.decode.AXMLDoc;
import com.zzzmode.apkeditor.utils.FileUtils;
import com.zzzmode.apkeditor.apksigner.ZipManager;
import com.zzzmode.apkeditor.axmleditor.decode.BXMLNode;
import com.zzzmode.apkeditor.axmleditor.decode.BTagNode;
import com.zzzmode.apkeditor.axmleditor.decode.StringBlock;

import java.io.*;
import java.util.concurrent.*;

public class ApkEditor {

  private static volatile ExecutorService executor;

  private static synchronized ExecutorService getExecutor() {
    if (executor == null || executor.isShutdown()) {
      executor = Executors.newSingleThreadExecutor();
    }
    return executor;
  }

  public static synchronized void shutdown() {
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }

  public interface CreateListener {
    void onStart();

    void onProgress(int step, String message);

    void onSuccess();

    void onError(Exception e);

    void onComplete();
  }

  private static final String WORK_DIR;
  private String versionName;
  private String versionCode;
  private String packageName;
  private String[] permissions;
  private Integer minSdkVersion;
  private Integer targetSdkVersion;

  static {
    String dir = null;
    try {
      dir =
          File.createTempFile(ApkEditor.class.getName(), null).getParentFile().getAbsolutePath()
              + "/apkeditor_work";
    } catch (Throwable e) {
      throw new RuntimeException(e);
    } finally {
      WORK_DIR = dir;
    }
  }

  private static final String A_XML_PREFIX = WORK_DIR + "/AndroidManifest_";

  private final String tmpApkPath;
  private final String manifestPath;

  public ApkEditor() {
    new File(WORK_DIR).mkdirs();
    long unique = System.nanoTime();
    tmpApkPath = WORK_DIR + "/tmp_" + unique + ".apk";
    manifestPath = A_XML_PREFIX + unique + ".xml";
  }

  private String origFile;
  private String outFile;
  private String appName;
  private String appIcon;
  private CreateListener createListener;

  public void setOrigFile(String origFile) {
    this.origFile = origFile;
  }

  public void setOutFile(String outFile) {
    this.outFile = outFile;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public void setAppIcon(String appIcon) {
    this.appIcon = appIcon;
  }

  public void setVersionName(String versionName) {
    this.versionName = versionName;
  }

  public void setVersionCode(String versionCode) {
    this.versionCode = versionCode;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public void setMinSdkVersion(int minSdkVersion) {
    this.minSdkVersion = minSdkVersion;
  }

  public void setTargetSdkVersion(int targetSdkVersion) {
    this.targetSdkVersion = targetSdkVersion;
  }

  public void setPermissions(String[] permissions) {
    this.permissions = permissions;
  }

  public void setCreateListener(CreateListener listener) {
    this.createListener = listener;
  }

  public boolean create() throws Exception {
    File tmpFile = null;
    File newXML = null;

    try {
      File origAPK = new File(origFile);
      tmpFile = new File(tmpApkPath);
      newXML = new File(manifestPath);

      notifyProgress(1, "Copying APK...");
      FileUtils.copyFile(origAPK, tmpFile);

      notifyProgress(2, "Extracting manifest...");
      ZipManager.extraZipEntry(tmpFile, new String[] {"AndroidManifest.xml"}, new String[] {manifestPath});

      if (appName != null) {
        notifyProgress(3, "Modifying label...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));
        LabelGlobalEditor labelEditor = new LabelGlobalEditor(doc);
        labelEditor.setEditorInfo(appName);
        labelEditor.commit();
        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      if (packageName != null) {
        notifyProgress(4, "Patching package references...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));

        BTagNode manifestNode = (BTagNode) doc.getManifestNode();
        java.util.List<BXMLNode> children = manifestNode.getChildren();
        java.util.Iterator<BXMLNode> it = children.iterator();

        while (it.hasNext()) {
          BXMLNode node = it.next();
          if (node instanceof BTagNode) {
            BTagNode tag = (BTagNode) node;

            String tagName = doc.getStringBlock().getStringFor(tag.getName());
            if ("permission".equals(tagName)) {
              BTagNode.Attribute[] attrs = tag.getAttribute();
              for (BTagNode.Attribute attr : attrs) {
                String attrName = doc.getStringBlock().getStringFor(attr.mName);
                if ("name".equals(attrName)) {
                  String permissionName = doc.getStringBlock().getStringFor(attr.mValue);
                  if (permissionName != null
    && permissionName.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) {
                    it.remove();
                    break;
                  }
                }
              }
            }
          }
        }

        AuthoritiesEditor authEditor = new AuthoritiesEditor(doc);
        AuthoritiesEditor.EditorInfo authInfo =
            new AuthoritiesEditor.EditorInfo("com.briarlua", packageName);
        authEditor.setEditorInfo(authInfo);
        authEditor.commit();

        PackageInfoEditor pkgEditor = new PackageInfoEditor(doc);
        PackageInfoEditor.EditorInfo pkgInfo = new PackageInfoEditor.EditorInfo();
        pkgInfo.setPackageName(packageName);
        pkgEditor.setEditorInfo(pkgInfo);
        pkgEditor.commit();

        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      if (versionCode != null) {
        notifyProgress(5, "Setting versionCode...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));
        PackageInfoEditor packageEditor = new PackageInfoEditor(doc);
        PackageInfoEditor.EditorInfo info = new PackageInfoEditor.EditorInfo();
        info.setVersionCode(Integer.parseInt(versionCode));
        packageEditor.setEditorInfo(info);
        packageEditor.commit();
        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      if (versionName != null) {
        notifyProgress(6, "Setting versionName...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));
        PackageInfoEditor packageEditor = new PackageInfoEditor(doc);
        PackageInfoEditor.EditorInfo info = new PackageInfoEditor.EditorInfo();
        info.setVersionName(versionName);
        packageEditor.setEditorInfo(info);
        packageEditor.commit();
        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      if (permissions != null) {
        notifyProgress(7, "Replacing permissions...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));
        PermissionEditor.EditorInfo info = new PermissionEditor.EditorInfo().clearAll();
        for (String p : permissions) info.with(new PermissionEditor.PermissionOpera(p).add());
        PermissionEditor permissionEditor = new PermissionEditor(doc);
        permissionEditor.setEditorInfo(info);
        permissionEditor.commit();
        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      if (minSdkVersion != null || targetSdkVersion != null) {
        notifyProgress(6, "Setting uses-sdk...");
        AXMLDoc doc = new AXMLDoc();
        doc.parse(new FileInputStream(newXML));

        UsesSdkEditor.EditorInfo sdkInfo =
            new UsesSdkEditor.EditorInfo(
                minSdkVersion != null ? minSdkVersion : 21,
                targetSdkVersion != null ? targetSdkVersion : 29);

        UsesSdkEditor sdkEditor = new UsesSdkEditor(doc);
        sdkEditor.setEditorInfo(sdkInfo);
        sdkEditor.commit();

        doc.build(new FileOutputStream(newXML));
        doc.release();
      }

      notifyProgress(8, "Replacing resources...");
      if (appIcon != null && !appIcon.isEmpty()) {
        ZipManager.replaceZipEntry(
            tmpFile,
            new String[] {"AndroidManifest.xml", "res/drawable/icon.png"},
            new String[] {manifestPath, appIcon});
      } else {
        ZipManager.replaceZipEntry(
            tmpFile, new String[] {"AndroidManifest.xml"}, new String[] {manifestPath});
      }

      notifyProgress(9, "Generating final APK...");
      FileUtils.copyFile(tmpFile, new File(outFile));
      notifyProgress(10, "Cleaning up...");

      return true;

    } catch (Exception e) {
      if (createListener != null) createListener.onError(e);
      throw e;
    } finally {
      if (tmpFile != null && tmpFile.exists()) tmpFile.delete();
      if (newXML != null && newXML.exists()) newXML.delete();
    }
  }

  public void createAsync() {
    if (createListener != null) createListener.onStart();
    getExecutor()
        .submit(
            () -> {
              try {
                create();
                if (createListener != null) createListener.onSuccess();
              } catch (Exception e) {
                if (createListener != null) createListener.onError(e);
              } finally {
                if (createListener != null) createListener.onComplete();
              }
            });
  }

  private void notifyProgress(int step, String message) {
    if (createListener != null) createListener.onProgress(step, message);
  }

  public static File getWorkDir() {
    return new File(WORK_DIR);
  }
}
