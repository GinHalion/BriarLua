package com.androlua;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.luajava.LuaState;
import com.luajava.LuaTable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LuaApplication extends Application implements LuaContext {

    private static final String TAG = "LuaApplication";
    private static LuaApplication mApp;
    private static final HashMap<String, Object> data = new HashMap<>();

    protected String localDir;
    protected String odexDir;
    protected String libDir;
    protected String luaMdDir;
    protected String luaCpath;
    protected String luaLpath = "";
    protected String luaExtDir;
    private SharedPreferences mSharedPreferences;

    public static LuaApplication getInstance() {
        return mApp;
    }

    public Uri getUriForPath(String path) {
        try {
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(path));
        } catch (Exception e) {
            Log.e(TAG, "getUriForPath failed: " + path, e);
            return null;
        }
    }

    public Uri getUriForFile(File path) {
        try {
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", path);
        } catch (Exception e) {
            Log.e(TAG, "getUriForFile failed", e);
            return null;
        }
    }

    public String getPathFromUri(Uri uri) {
        if (uri == null) return null;
        String path = null;
        try {
            String scheme = uri.getScheme();
            if ("content".equals(scheme)) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int idx = cursor.getColumnIndex("_data");
                            if (idx >= 0) path = cursor.getString(idx);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } else if ("file".equals(scheme)) {
                path = uri.getPath();
            }
        } catch (Exception e) {
            Log.e(TAG, "getPathFromUri failed", e);
        }
        return path;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mApp = this;

        try {
            com.briarlua.CrashManager.install(this);
        } catch (Throwable t) {
            Log.e(TAG, "CrashManager install failed", t);
        }

        mSharedPreferences = getSharedPreferences(this);

        try {
            initDirs();
        } catch (Exception e) {
            Log.e(TAG, "initDirs failed, fallback to internal storage", e);
            fallbackToInternal();
        }

        StringBuilder lp = new StringBuilder();
        lp.append(luaMdDir).append("/?.lua;");
        lp.append(luaMdDir).append("/lua/?.lua;");
        lp.append(luaMdDir).append("/?/init.lua;");
        lp.append(localDir).append("/?.lua;");
        lp.append(localDir).append("/lua/?.lua;");
        lp.append(localDir).append("/?/init.lua;");
        luaLpath = lp.toString();

        luaCpath = getApplicationInfo().nativeLibraryDir + "/lib?.so;" + libDir + "/lib?.so";

        try {
            com.briarlua.utils.ClassCatalog.warmAsync(this);
        } catch (Throwable ignored) {
        }
    }

    private void initDirs() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            luaExtDir = sdDir + "/BriarLua";
        } else {
            File storageDir = new File("/storage");
            File[] fs = storageDir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    String[] list = f.list();
                    if (list != null && list.length > 5) {
                        luaExtDir = f.getAbsolutePath() + "/BriarLua";
                        break;
                    }
                }
            }
            if (luaExtDir == null) {
                luaExtDir = getDir("BriarLua", Context.MODE_PRIVATE).getAbsolutePath();
            }
        }

        File destDir = new File(luaExtDir);
        if (!destDir.exists() && !destDir.mkdirs()) {
            luaExtDir = getFilesDir().getAbsolutePath() + "/BriarLua";
            destDir = new File(luaExtDir);
            destDir.mkdirs();
        }

        localDir = getFilesDir().getAbsolutePath();
        odexDir = getDir("odex", Context.MODE_PRIVATE).getAbsolutePath();
        libDir = getDir("lib", Context.MODE_PRIVATE).getAbsolutePath();
        luaMdDir = getDir("lua", Context.MODE_PRIVATE).getAbsolutePath();
    }

    private void fallbackToInternal() {
        localDir = getFilesDir().getAbsolutePath();
        odexDir = getDir("odex", Context.MODE_PRIVATE).getAbsolutePath();
        libDir = getDir("lib", Context.MODE_PRIVATE).getAbsolutePath();
        luaMdDir = getDir("lua", Context.MODE_PRIVATE).getAbsolutePath();
        luaExtDir = getFilesDir().getAbsolutePath() + "/BriarLua";
        new File(luaExtDir).mkdirs();
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }


    @Override
    public ArrayList<ClassLoader> getClassLoaders() {
        return new ArrayList<>();
    }

    @Override
    public void regGc(LuaGcable obj) {
    }

    @Override
    public String getLuaPath() {
        return localDir;
    }

    @Override
    public String getLuaPath(String path) {
        return new File(getLuaDir(), path).getAbsolutePath();
    }

    @Override
    public String getLuaPath(String dir, String name) {
        return new File(getLuaDir(dir), name).getAbsolutePath();
    }

    @Override
    public String getLuaExtPath(String path) {
        return new File(getLuaExtDir(), path).getAbsolutePath();
    }

    @Override
    public String getLuaExtPath(String dir, String name) {
        return new File(getLuaExtDir(dir), name).getAbsolutePath();
    }

    @Override
    public String getLuaDir() {
        return localDir;
    }

    @Override
    public String getLuaDir(String dir) {
        return new File(localDir, dir).getAbsolutePath();
    }

    @Override
    public String getLuaExtDir() {
        return luaExtDir;
    }

    @Override
    public String getLuaExtDir(String name) {
        if (name == null || name.isEmpty()) return luaExtDir;
        File dir = new File(luaExtDir, name);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    @Override
    public void setLuaExtDir(String dir) {
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();
                luaExtDir = new File(sdDir, dir).getAbsolutePath();
            } else {
                luaExtDir = getDir(dir, Context.MODE_PRIVATE).getAbsolutePath();
            }
            File destDir = new File(luaExtDir);
            if (!destDir.exists()) destDir.mkdirs();
        } catch (Exception e) {
            Log.e(TAG, "setLuaExtDir failed", e);
        }
    }

    public String getLocalDir() {
        return localDir;
    }

    public String getMdDir() {
        return luaMdDir;
    }

    public String getLibDir() {
        return libDir;
    }

    public String getOdexDir() {
        return odexDir;
    }

    public int getWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    public int getHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public String getLuaLpath() {
        return luaLpath;
    }

    @Override
    public String getLuaCpath() {
        return luaCpath;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public LuaState getLuaState() {
        return null;
    }

    @Override
    public Object doFile(String path, Object[] arg) {
        return null;
    }


    @Override
    public void call(String name, Object[] args) {
    }

    @Override
    public void set(String name, Object object) {
        if (name != null) data.put(name, object);
    }

    public Object get(String name) {
        return data.get(name);
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return data;
    }

    public void clearSharedData() {
        try {
            mSharedPreferences.edit().clear().apply();
        } catch (Exception e) {
            Log.e(TAG, "clearSharedData failed", e);
        }
    }

    @Override
    public Map<String, ?> getSharedData() {
        return mSharedPreferences.getAll();
    }

    @Override
    public Object getSharedData(String key) {
        if (key == null) return null;
        return mSharedPreferences.getAll().get(key);
    }

    @Override
    public Object getSharedData(String key, Object def) {
        if (key == null) return def;
        Object ret = mSharedPreferences.getAll().get(key);
        return ret != null ? ret : def;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean setSharedData(String key, Object value) {
        if (key == null) return false;
        try {
            SharedPreferences.Editor edit = mSharedPreferences.edit();
            if (value == null) {
                edit.remove(key);
            } else if (value instanceof String) {
                edit.putString(key, (String) value);
            } else if (value instanceof Long) {
                edit.putLong(key, (Long) value);
            } else if (value instanceof Integer) {
                edit.putInt(key, (Integer) value);
            } else if (value instanceof Float) {
                edit.putFloat(key, (Float) value);
            } else if (value instanceof Boolean) {
                edit.putBoolean(key, (Boolean) value);
            } else if (value instanceof Set) {
                edit.putStringSet(key, (Set<String>) value);
            } else if (value instanceof LuaTable) {
                LuaTable table = (LuaTable) value;
                Set<String> set = new HashSet<>();
                for (int i = 1; i <= table.length(); i++) {
                    Object val = table.get(i);
                    if (val instanceof String) set.add((String) val);
                }
                edit.putStringSet(key, set);
            } else {
                return false;
            }
            edit.apply();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "setSharedData failed: " + key, e);
            return false;
        }
    }

    @Override
    public void sendMsg(String msg) {
        if (msg == null) return;
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "sendMsg failed: " + msg, e);
        }
    }

    @Override
    public void sendError(String title, Exception msg) {
        Log.e(TAG, title == null ? "error" : title, msg);
    }
}