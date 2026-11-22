
package com.luajava;


import android.util.Log;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class LuaState {

    private final static String LUAJAVA_LIB = "luajava";

    final public static int LUAI_MAXSTACK = 1000000;
    final public static int LUA_REGISTRYINDEX = -LUAI_MAXSTACK - 1000;

    final public static int LUA_RIDX_MAINTHREAD = 1;
    final public static int LUA_RIDX_GLOBALS = 2;
    final public static int LUA_RIDX_LAST = LUA_RIDX_GLOBALS;

    final public static int LUA_TNONE = -1;
    final public static int LUA_TNIL = 0;
    final public static int LUA_TBOOLEAN = 1;
    final public static int LUA_TLIGHTUSERDATA = 2;
    final public static int LUA_TNUMBER = 3;
    final public static int LUA_TSTRING = 4;
    final public static int LUA_TTABLE = 5;
    final public static int LUA_TFUNCTION = 6;
    final public static int LUA_TUSERDATA = 7;
    final public static int LUA_TTHREAD = 8;
    final public static int LUA_TINTEGER = 9;

    final public static int LUA_MULTRET = -1;


    final public static int LUA_YIELD = 1;
    final public static int LUA_ERRRUN = 2;

    final public static int LUA_ERRSYNTAX = 3;

    final public static int LUA_ERRMEM = 4;

    final public static int LUA_ERRGCMM = 5;
    final public static int LUA_ERRERR = 6;

    final public static int LUA_OPEQ = 0;
    final public static int LUA_OPLT = 1;
    final public static int LUA_OPLE = 2;


    static {
        System.loadLibrary(LUAJAVA_LIB);
    }

    private long luaState;

    private final Map<Object, Long> mObjectTokens =
            Collections.synchronizedMap(new WeakHashMap<Object, Long>());
    private long mTokenSeq = 0;
    private int mNewUserdataCount = 0;


    protected LuaState() {
        luaState = _newstate();
    }

    protected LuaState(long luaState) {
        this.luaState = luaState;
        LuaStateFactory.insertLuaState(this);
    }

    public synchronized void close() {
        if (luaState == 0) return;
        long ptr = luaState;
        LuaStateFactory.removeLuaState(ptr);
        _close(ptr);
        this.luaState = 0;
    }

    @Override
    protected void finalize() {
        Log.i("luaState", "finalize: "+luaState);
        try {
           close();
        }
        catch (Exception e) {
            System.err.println("Unable to release luaState " + luaState);
        }
    }

    public synchronized boolean isClosed() {
        return luaState == 0;
    }

    private void checkOpen() {
        if (luaState == 0) {
            throw new IllegalStateException("LuaState is closed");
        }
    }

    public long getPointer() {
        return luaState;
    }

    private com.androlua.LuaContext mContext;

    public void pushContext(com.androlua.LuaContext context) {
        mContext = context;
        pushString("_LuaContext");
        pushJavaObject(context);
        setTable(LUA_REGISTRYINDEX);
    }

    public com.androlua.LuaContext getContext() {
        return mContext;
    }


    private synchronized native long _newstate();

    private synchronized native void _close(long ptr);

    private synchronized native long _newthread(long ptr);

    private synchronized native int _getTop(long ptr);

    private synchronized native void _setTop(long ptr, int idx);

    private synchronized native void _pushValue(long ptr, int idx);

    private synchronized native void _rotate(long ptr, int idx, int n);

    private synchronized native void _copy(long ptr, int fromidx, int toidx);

    private synchronized native void _remove(long ptr, int idx);

    private synchronized native void _insert(long ptr, int idx);

    private synchronized native void _replace(long ptr, int idx);

    private synchronized native int _checkStack(long ptr, int sz);

    private synchronized native void _xmove(long from, long to, int n);

    private synchronized native int _isNumber(long ptr, int idx);

    private synchronized native int _isInteger(long ptr, int idx);

    private synchronized native int _isString(long ptr, int idx);

    private synchronized native int _isCFunction(long ptr, int idx);

    private synchronized native int _isUserdata(long ptr, int idx);

    private synchronized native int _type(long ptr, int idx);

    private synchronized native String _typeName(long ptr, int tp);

    private synchronized native int _equal(long ptr, int idx1, int idx2);

    private synchronized native int _compare(long ptr, int idx1, int idx2, int op);

    private synchronized native int _rawequal(long ptr, int idx1, int idx2);

    private synchronized native int _lessThan(long ptr, int idx1, int idx2);

    private synchronized native double _toNumber(long ptr, int idx);

    private synchronized native long _toInteger(long ptr, int idx);

    private synchronized native int _toBoolean(long ptr, int idx);

    private synchronized native String _toString(long ptr, int idx);

    private synchronized native byte[] _toBuffer(long ptr, int idx);

    private synchronized native int _objlen(long ptr, int idx);

    private synchronized native int _rawlen(long ptr, int idx);

    private synchronized native long _toThread(long ptr, int idx);

    private synchronized native void _pushNil(long ptr);

    private synchronized native void _pushNumber(long ptr, double number);

    private synchronized native void _pushInteger(long ptr, long integer);

    private synchronized native void _pushString(long ptr, String str);

    private synchronized native void _pushLString(long ptr, byte[] bytes, int n);

    private synchronized native void _pushBoolean(long ptr, int bool);

    private synchronized native int _getTable(long ptr, int idx);

    private synchronized native int _getField(long ptr, int idx, String k);

    private synchronized native int _getI(long ptr, int idx, long n);

    private synchronized native int _rawGet(long ptr, int idx);

    private synchronized native int _rawGetI(long ptr, int idx, long n);

    private synchronized native void _createTable(long ptr, int narr, int nrec);

    private synchronized native int _getMetaTable(long ptr, int idx);

    private synchronized native int _getUserValue(long ptr, int idx);

    private synchronized native void _setTable(long ptr, int idx);

    private synchronized native void _setField(long ptr, int idx, String k);

    private synchronized native void _setI(long ptr, int idx, long n);

    private synchronized native void _rawSet(long ptr, int idx);

    private synchronized native void _rawSetI(long ptr, int idx, long n);

    private synchronized native int _setMetaTable(long ptr, int idx);

    private synchronized native void _setUserValue(long ptr, int idx);

    private synchronized native void _call(long ptr, int nArgs, int nResults);

    private synchronized native int _pcall(long ptr, int nArgs, int Results, int errFunc);

    private synchronized native int _yield(long ptr, int nResults);

    private synchronized native int _resume(long ptr, long from, int nargs);

    private synchronized native int _status(long ptr);

    private synchronized native int _isYieldable(long ptr);

    final public static int LUA_GCSTOP = 0;
    final public static int LUA_GCRESTART = 1;
    final public static int LUA_GCCOLLECT = 2;
    final public static int LUA_GCCOUNT = 3;
    final public static int LUA_GCCOUNTB = 4;
    final public static int LUA_GCSTEP = 5;
    final public static int LUA_GCSETPAUSE = 6;
    final public static int LUA_GCSETSTEPMUL = 7;

    private synchronized native int _gc(long ptr, int what, int data);

    private synchronized native int _error(long ptr);

    private synchronized native int _next(long ptr, int idx);

    private synchronized native void _concat(long ptr, int n);

    private synchronized native void _pop(long ptr, int n);

    private synchronized native void _newTable(long ptr);

    private synchronized native int _strlen(long ptr, int idx);

    private synchronized native int _isFunction(long ptr, int idx);

    private synchronized native int _isTable(long ptr, int idx);

    private synchronized native int _isNil(long ptr, int idx);

    private synchronized native int _isBoolean(long ptr, int idx);

    private synchronized native int _isThread(long ptr, int idx);

    private synchronized native int _isNone(long ptr, int idx);

    private synchronized native int _isNoneOrNil(long ptr, int idx);

    private synchronized native void _pushGlobalTable(long ptr);

    private synchronized native void _setGlobal(long ptr, String name);

    private synchronized native int _getGlobal(long ptr, String name);


    private synchronized native int _LdoFile(long ptr, String fileName);

    private synchronized native int _LdoString(long ptr, String string);

    private synchronized native int _LgetMetaField(long ptr, int obj, String e);

    private synchronized native int _LcallMeta(long ptr, int obj, String e);

    private synchronized native int _LargError(long ptr, int numArg, String extraMsg);

    private synchronized native String _LcheckString(long ptr, int numArg);

    private synchronized native String _LoptString(long ptr, int numArg, String def);

    private synchronized native double _LcheckNumber(long ptr, int numArg);

    private synchronized native double _LoptNumber(long ptr, int numArg, double def);

    private synchronized native int _LcheckInteger(long ptr, int numArg);

    private synchronized native int _LoptInteger(long ptr, int numArg, int def);

    private synchronized native void _LcheckStack(long ptr, int sz, String msg);

    private synchronized native void _LcheckType(long ptr, int nArg, int t);

    private synchronized native void _LcheckAny(long ptr, int nArg);

    private synchronized native int _LnewMetatable(long ptr, String tName);

    private synchronized native void _LgetMetatable(long ptr, String tName);

    private synchronized native void _Lwhere(long ptr, int lvl);

    private synchronized native int _Lref(long ptr, int t);

    private synchronized native void _LunRef(long ptr, int t, int ref);

    private synchronized native int _LloadFile(long ptr, String fileName);

    private synchronized native int _LloadBuffer(long ptr, byte[] buff, long sz, String name);

    private synchronized native int _LloadString(long ptr, String s);

    private synchronized native String _Lgsub(long ptr, String s, String p, String r);

    private synchronized native String _getUpValue(long ptr, int funcindex, int n);

    private synchronized native String _setUpValue(long ptr, int funcindex, int n);

    private synchronized native byte[] _dump(long ptr, int funcindex);

    private synchronized native void _openBase(long ptr);

    private synchronized native void _openTable(long ptr);

    private synchronized native void _openIo(long ptr);

    private synchronized native void _openOs(long ptr);

    private synchronized native void _openString(long ptr);

    private synchronized native void _openMath(long ptr);

    private synchronized native void _openDebug(long ptr);

    private synchronized native void _openPackage(long ptr);

    private synchronized native void _openLibs(long ptr);


    public LuaState newThread() {
        checkOpen();
        LuaState l = new LuaState(_newthread(luaState));
        LuaStateFactory.insertLuaState(l);
        return l;
    }


    public int getTop() {
        checkOpen();
        return _getTop(luaState);
    }

    public void setTop(int idx) {
        checkOpen();
        _setTop(luaState, idx);
    }

    public void pushValue(int idx) {
        checkOpen();
        _pushValue(luaState, idx);
    }

    public void rotate(int idx, int n) {
        checkOpen();
        _rotate(luaState, idx, n);
    }

    public void copy(int fromidx, int toidx) {
        checkOpen();
        _copy(luaState, fromidx, toidx);
    }

    public void remove(int idx) {
        checkOpen();
        _remove(luaState, idx);
    }

    public void insert(int idx) {
        checkOpen();
        _insert(luaState, idx);
    }

    public void replace(int idx) {
        checkOpen();
        _replace(luaState, idx);
    }

    public int checkStack(int sz) {
        checkOpen();
        return _checkStack(luaState, sz);
    }

    public void xmove(LuaState to, int n) {
        checkOpen();
        _xmove(luaState, to.luaState, n);
    }


    public boolean isNumber(int idx) {
        checkOpen();
        return (_isNumber(luaState, idx) != 0);
    }

    public boolean isInteger(int idx) {
        checkOpen();
        return (_isInteger(luaState, idx) != 0);
    }

    public boolean isString(int idx) {
        checkOpen();
        return (_isString(luaState, idx) != 0);
    }

    public boolean isFunction(int idx) {
        checkOpen();
        return (_isFunction(luaState, idx) != 0);
    }

    public boolean isCFunction(int idx) {
        checkOpen();
        return (_isCFunction(luaState, idx) != 0);
    }

    public boolean isUserdata(int idx) {
        checkOpen();
        return (_isUserdata(luaState, idx) != 0);
    }

    public boolean isTable(int idx) {
        checkOpen();
        return (_isTable(luaState, idx) != 0);
    }

    public boolean isBoolean(int idx) {
        checkOpen();
        return (_isBoolean(luaState, idx) != 0);
    }

    public boolean isNil(int idx) {
        checkOpen();
        return (_isNil(luaState, idx) != 0);
    }

    public boolean isThread(int idx) {
        checkOpen();
        return (_isThread(luaState, idx) != 0);
    }

    public boolean isNone(int idx) {
        checkOpen();
        return (_isNone(luaState, idx) != 0);
    }

    public boolean isNoneOrNil(int idx) {
        checkOpen();
        return (_isNoneOrNil(luaState, idx) != 0);
    }

    public int type(int idx) {
        checkOpen();
        return _type(luaState, idx);
    }

    public String typeName(int tp) {
        checkOpen();
        return _typeName(luaState, tp);
    }

    public int equal(int idx1, int idx2) {
        checkOpen();
        return _equal(luaState, idx1, idx2);
    }

    public int compare(int idx1, int idx2, int op) {
        checkOpen();
        return _compare(luaState, idx1, idx2, op);
    }

    public int rawequal(int idx1, int idx2) {
        checkOpen();
        return _rawequal(luaState, idx1, idx2);
    }

    public int lessThan(int idx1, int idx2) {
        checkOpen();
        return _lessThan(luaState, idx1, idx2);
    }

    public double toNumber(int idx) {
        checkOpen();
        return _toNumber(luaState, idx);
    }

    public long toInteger(int idx) {
        checkOpen();
        return _toInteger(luaState, idx);
    }

    public boolean toBoolean(int idx) {
        checkOpen();
        return (_toBoolean(luaState, idx) != 0);
    }

    public String toString(int idx) {
        checkOpen();
        return _toString(luaState, idx);
    }

    public byte[] toBuffer(int idx) {
        checkOpen();
        return _toBuffer(luaState, idx);
    }

    public int strLen(int idx) {
        checkOpen();
        return _strlen(luaState, idx);
    }

    public int objLen(int idx) {
        checkOpen();
        return _objlen(luaState, idx);
    }

    public int rawLen(int idx) {
        checkOpen();
        return _rawlen(luaState, idx);
    }

    public LuaState toThread(int idx) {
        checkOpen();
        return new LuaState(_toThread(luaState, idx));
    }


    public void pushNil() {
        checkOpen();
        _pushNil(luaState);
    }

    public void pushNumber(double db) {
        checkOpen();
        _pushNumber(luaState, db);
    }

    public void pushInteger(long integer) {
        checkOpen();
        _pushInteger(luaState, integer);
    }

    public void pushString(String str) {
        checkOpen();
        if (str == null)
            _pushNil(luaState);
        else
            _pushString(luaState, str);
    }

    public void pushString(byte[] bytes) {
        checkOpen();
        if (bytes == null)
            _pushNil(luaState);
        else
            _pushLString(luaState, bytes, bytes.length);
    }

    public void pushBoolean(boolean bool) {
        checkOpen();
        _pushBoolean(luaState, bool ? 1 : 0);
    }


    public int getTable(int idx) {
        checkOpen();
        return _getTable(luaState, idx);
    }

    public int getField(int idx, String k) {
        checkOpen();
        return _getField(luaState, idx, k);
    }

    public int getI(int idx, long n) {
        checkOpen();
        return _getI(luaState, idx, n);
    }

    public int rawGet(int idx) {
        checkOpen();
        return _rawGet(luaState, idx);
    }

    public int rawGetI(int idx, long n) {
        checkOpen();
        return _rawGetI(luaState, idx, n);
    }

    public void createTable(int narr, int nrec) {
        checkOpen();
        _createTable(luaState, narr, nrec);
    }

    public void newTable() {
        checkOpen();
        _newTable(luaState);
    }

    public int getMetaTable(int idx) {
        checkOpen();
        return _getMetaTable(luaState, idx);
    }

    public int getUserValue(int idx) {
        checkOpen();
        return _getUserValue(luaState, idx);
    }


    public void setTable(int idx) {
        checkOpen();
        _setTable(luaState, idx);
    }

    public void setField(int idx, String k) {
        checkOpen();
        _setField(luaState, idx, k);
    }

    public void setI(int idx, long n) {
        checkOpen();
        _setI(luaState, idx, n);
    }

    public void rawSet(int idx) {
        checkOpen();
        _rawSet(luaState, idx);
    }

    public void rawSetI(int idx, long n) {
        checkOpen();
        _rawSetI(luaState, idx, n);
    }

    public int setMetaTable(int idx) {
        checkOpen();
        return _setMetaTable(luaState, idx);
    }

    public void setUserValue(int idx) {
        checkOpen();
        _setUserValue(luaState, idx);
    }

    public void call(int nArgs, int nResults) {
        checkOpen();
        _call(luaState, nArgs, nResults);
    }

    public int pcall(int nArgs, int nResults, int errFunc) {
        checkOpen();
        return _pcall(luaState, nArgs, nResults, errFunc);
    }

    public int yield(int nResults) {
        checkOpen();
        return _yield(luaState, nResults);
    }

    public int resume(LuaState from, int nArgs) {
        checkOpen();
        return _resume(luaState, from.getPointer(), nArgs);
    }

    public int status() {
        checkOpen();
        return _status(luaState);
    }

    public int isYieldable() {
        checkOpen();
        return _isYieldable(luaState);
    }

    public int gc(int what, int data) {
        checkOpen();
        return _gc(luaState, what, data);
    }


    public int next(int idx) {
        checkOpen();
        return _next(luaState, idx);
    }

    public int error() {
        checkOpen();
        return _error(luaState);
    }

    public void concat(int n) {
        checkOpen();
        _concat(luaState, n);
    }


    public int LdoFile(String fileName) {
        checkOpen();
        return _LdoFile(luaState, fileName);
    }

    public int LdoString(String str) {
        checkOpen();
        return _LdoString(luaState, str);
    }

    public int LgetMetaField(int obj, String e) {
        checkOpen();
        return _LgetMetaField(luaState, obj, e);
    }

    public int LcallMeta(int obj, String e) {
        checkOpen();
        return _LcallMeta(luaState, obj, e);
    }


    public int LargError(int numArg, String extraMsg) {
        checkOpen();
        return _LargError(luaState, numArg, extraMsg);
    }

    public String LcheckString(int numArg) {
        checkOpen();
        return _LcheckString(luaState, numArg);
    }

    public String LoptString(int numArg, String def) {
        checkOpen();
        return _LoptString(luaState, numArg, def);
    }

    public double LcheckNumber(int numArg) {
        checkOpen();
        return _LcheckNumber(luaState, numArg);
    }

    public double LoptNumber(int numArg, double def) {
        checkOpen();
        return _LoptNumber(luaState, numArg, def);
    }

    public int LcheckInteger(int numArg) {
        checkOpen();
        return _LcheckInteger(luaState, numArg);
    }

    public int LoptInteger(int numArg, int def) {
        checkOpen();
        return _LoptInteger(luaState, numArg, def);
    }

    public void LcheckStack(int sz, String msg) {
        checkOpen();
        _LcheckStack(luaState, sz, msg);
    }

    public void LcheckType(int nArg, int t) {
        checkOpen();
        _LcheckType(luaState, nArg, t);
    }

    public void LcheckAny(int nArg) {
        checkOpen();
        _LcheckAny(luaState, nArg);
    }

    public int LnewMetatable(String tName) {
        checkOpen();
        return _LnewMetatable(luaState, tName);
    }

    public void LgetMetatable(String tName) {
        checkOpen();
        _LgetMetatable(luaState, tName);
    }

    public void Lwhere(int lvl) {
        checkOpen();
        _Lwhere(luaState, lvl);
    }

    public int Lref(int t) {
        checkOpen();
        return _Lref(luaState, t);
    }

    public void LunRef(int t, int ref) {
        checkOpen();
        _LunRef(luaState, t, ref);
    }

    public int LloadFile(String fileName) {
        checkOpen();
        return _LloadFile(luaState, fileName);
    }

    public int LloadString(String s) {
        checkOpen();
        return _LloadString(luaState, s);
    }

    public int LloadBuffer(byte[] buff, String name) {
        checkOpen();
        return _LloadBuffer(luaState, buff, buff.length, name);
    }

    public String Lgsub(String s, String p, String r) {
        checkOpen();
        return _Lgsub(luaState, s, p, r);
    }

    public String getUpValue(int funcindex, int n) {
        checkOpen();
        return _getUpValue(luaState, funcindex, n);
    }

    public String setUpValue(int funcindex, int n) {
        checkOpen();
        return _setUpValue(luaState, funcindex, n);
    }

    public byte[] dump(int funcindex) {
        checkOpen();
        return _dump(luaState, funcindex);
    }


    public void pop(int n) {
        checkOpen();
        _pop(luaState, n);
    }


    public synchronized void pushGlobalTable() {
        checkOpen();
        _pushGlobalTable(luaState);
    }

    public synchronized int getGlobal(String global) {
        checkOpen();
        return _getGlobal(luaState, global);
    }

    public synchronized void setGlobal(String name) {
        checkOpen();
        _setGlobal(luaState, name);
    }

    public void openBase() {
        checkOpen();
        _openBase(luaState);
    }

    public void openTable() {
        checkOpen();
        _openTable(luaState);
    }

    public void openIo() {
        checkOpen();
        _openIo(luaState);
    }

    public void openOs() {
        checkOpen();
        _openOs(luaState);
    }

    public void openString() {
        checkOpen();
        _openString(luaState);
    }

    public void openMath() {
        checkOpen();
        _openMath(luaState);
    }

    public void openDebug() {
        checkOpen();
        _openDebug(luaState);
    }

    public void openPackage() {
        checkOpen();
        _openPackage(luaState);
    }

    public void openLibs() {
        checkOpen();
        _openLibs(luaState);
        _openLuajava(luaState);
        pushPrimitive();
    }



    private synchronized native void _openLuajava(long stateId);

    private synchronized native Object _getObjectFromUserdata(long L, int idx) throws LuaException;

    private synchronized native boolean _isObject(long L, int idx);

    private synchronized native void _pushJavaObject(long L, Object obj);

    private synchronized native boolean _pushJavaObject2(long L, Object obj, long token);

    private synchronized native void _pushJavaFunction(long L, JavaFunction func) throws LuaException;

    private synchronized native boolean _isJavaFunction(long L, int idx);

    public void openLuajava() {
        checkOpen();
        _openLuajava(luaState);
        pushPrimitive();
    }

    public Object getObjectFromUserdata(int idx) throws LuaException {
        checkOpen();
        return _getObjectFromUserdata(luaState, idx);
    }

    public boolean isObject(int idx) {
        checkOpen();
        return _isObject(luaState, idx);
    }

    public void pushJavaObject(Object obj) {
        checkOpen();
        long token;
        synchronized (mObjectTokens) {
            Long t = mObjectTokens.get(obj);
            if (t != null) {
                token = t;
            } else {
                mTokenSeq++;
                mObjectTokens.put(obj, mTokenSeq);
                token = mTokenSeq;
            }
        }
        if (_pushJavaObject2(luaState, obj, token)) {
            return;
        }
        if (!LuaJavaAPI.tryAcquireJavaObject()) {
            gc(LUA_GCCOLLECT, 0);
            LuaJavaAPI.ensureJavaObjectCapacity();
        }
        if ((++mNewUserdataCount & 0xFFF) == 0) {
            try {
                gc(LUA_GCSTEP, 4);
            } catch (Throwable ignored) {
            }
        }
    }

    public void pushJavaFunction(JavaFunction func) throws LuaException {
        checkOpen();
        if (!LuaJavaAPI.tryAcquireJavaObject()) {
            gc(LUA_GCCOLLECT, 0);
            LuaJavaAPI.ensureJavaObjectCapacity();
        }
        _pushJavaFunction(luaState, func);
    }

    public boolean isJavaFunction(int idx) {
        checkOpen();
        return _isJavaFunction(luaState, idx);
    }

    public void pushObjectValue(Object obj) throws LuaException {
        if (obj == null) {
            pushNil();
        } else if (obj instanceof Boolean) {
            Boolean bool = (Boolean) obj;
            pushBoolean(bool);
        } else if (obj instanceof Long) {
            pushInteger((Long) obj);
        } else if (obj instanceof Integer) {
            pushInteger((Integer) obj);
        } else if (obj instanceof Short) {
            pushInteger((Short) obj);
        } else if (obj instanceof Character) {
            pushInteger((Character) obj);
        } else if (obj instanceof Byte) {
            pushInteger((Byte) obj);
        } else if (obj instanceof Float) {
            pushNumber((Float) obj);
        } else if (obj instanceof Double) {
            pushNumber((Double) obj);
        } else if (obj instanceof String) {
            pushString((String) obj);
        } else if (obj instanceof LuaString) {
            pushString(((LuaString) obj).toByteArray());
        } else if (obj instanceof JavaFunction) {
            JavaFunction func = (JavaFunction) obj;
            pushJavaFunction(func);
        } else if (obj instanceof LuaObject) {
            LuaObject ref = (LuaObject) obj;
            if (ref.getLuaState() == this)
                ref.push();
            else
                pushJavaObject(ref);
        } else {
            pushJavaObject(obj);
        }
    }

    public synchronized Object toJavaObject(int idx) throws LuaException {
        Object obj = null;

        if (isBoolean(idx)) {
            obj = toBoolean(idx);
        } else if (type(idx) == LuaState.LUA_TSTRING) {
            obj = toString(idx);
        } else if (isFunction(idx)) {
            obj = getLuaObject(idx).getFunction();
        } else if (isTable(idx)) {
            obj = getLuaObject(idx).getTable();
        } else if (type(idx) == LuaState.LUA_TNUMBER) {
            if (isInteger(idx))
                obj = toInteger(idx);
            else
                obj = toNumber(idx);
        } else if (isUserdata(idx)) {
            if (isObject(idx)) {
                obj = getObjectFromUserdata(idx);
            } else {
                obj = getLuaObject(idx);
            }
        } else if (isNil(idx)) {
            obj = null;
        }

        return obj;
    }

    public LuaObject getLuaObject(String globalName) {
        pushGlobalTable();
        pushString(globalName);
        rawGet(-2);
        LuaObject obj = getLuaObject(-1);
        pop(2);
        return obj;
    }

    public LuaObject getLuaObject(LuaObject parent, String name)
            throws LuaException {
        return new LuaObject(parent, name);
    }

    public LuaObject getLuaObject(LuaObject parent, Number name)
            throws LuaException {
        return new LuaObject(parent, name);
    }

    public LuaObject getLuaObject(LuaObject parent, LuaObject name)
            throws LuaException {
        if (parent.getLuaState().getPointer() != luaState ||
                parent.getLuaState().getPointer() != name.getLuaState().getPointer())
            throw new LuaException("Object must have the same LuaState as the parent!");

        return new LuaObject(parent, name);
    }

    public LuaObject getLuaObject(int index) {
        if (isFunction(index))
            return new LuaFunction(this, index);
        else if (isTable(index))
            return new LuaTable(this, index);
        else
            return new LuaObject(this, index);
    }

    public static Number convertLuaNumber(Double db, Class<?> retType) {
        if (retType.isPrimitive()) {
            if (retType == Integer.TYPE) {
                return db.intValue();
            } else if (retType == Long.TYPE) {
                return db.longValue();
            } else if (retType == Float.TYPE) {
                return db.floatValue();
            } else if (retType == Double.TYPE) {
                return db.doubleValue();
            } else if (retType == Byte.TYPE) {
                return db.byteValue();
            } else if (retType == Short.TYPE) {
                return db.shortValue();
            }
        } else if (Number.class.isAssignableFrom(retType)) {
            if (retType.isAssignableFrom(Long.class)) {
                return new Long(db.longValue());
            } else if (retType.isAssignableFrom(Integer.class)) {
                return new Integer(db.intValue());
            } else if (retType.isAssignableFrom(Float.class)) {
                return new Float(db.floatValue());
            } else if (retType.isAssignableFrom(Double.class)) {
                return db;
            } else if (retType.isAssignableFrom(Byte.class)) {
                return new Byte(db.byteValue());
            } else if (retType.isAssignableFrom(Short.class)) {
                return new Short(db.shortValue());
            }
        }

        return null;
    }

    public static Number convertLuaNumber(Long lg, Class<?> retType) {
        if (retType.isPrimitive()) {
            if (retType == Integer.TYPE) {
                return lg.intValue();
            } else if (retType == Long.TYPE) {
                return lg.longValue();
            } else if (retType == Float.TYPE) {
                return lg.floatValue();
            } else if (retType == Double.TYPE) {
                return lg.doubleValue();
            } else if (retType == Byte.TYPE) {
                return lg.byteValue();
            } else if (retType == Short.TYPE) {
                return lg.shortValue();
            }
        } else if (Number.class.isAssignableFrom(retType)) {
            if (retType.isAssignableFrom(Long.class)) {
                return new Long(lg.longValue());
            } else if (retType.isAssignableFrom(Integer.class)) {
                return new Integer(lg.intValue());
            } else if (retType.isAssignableFrom(Float.class)) {
                return new Float(lg.floatValue());
            } else if (retType.isAssignableFrom(Double.class)) {
                return lg;
            } else if (retType.isAssignableFrom(Byte.class)) {
                return new Byte(lg.byteValue());
            } else if (retType.isAssignableFrom(Short.class)) {
                return new Short(lg.shortValue());
            }
        }

        return null;
    }

    public void pushPrimitive() {
        pushJavaObject(Boolean.TYPE);
        setGlobal("boolean");
        pushJavaObject(Byte.TYPE);
        setGlobal("byte");
        pushJavaObject(Character.TYPE);
        setGlobal("char");
        pushJavaObject(Short.TYPE);
        setGlobal("short");
        pushJavaObject(Integer.TYPE);
        setGlobal("int");
        pushJavaObject(Long.TYPE);
        setGlobal("long");
        pushJavaObject(Float.TYPE);
        setGlobal("float");
        pushJavaObject(Double.TYPE);
        setGlobal("double");
    }

    public LuaFunction getFunction(String name) {
        LuaObject obj = getLuaObject(name);
        if (obj.isFunction())
            return obj.getFunction();
        return null;
    }
    public LuaFunction getFunction(int idx) {
        LuaObject obj = getLuaObject(idx);
        if (obj.isFunction())
            return obj.getFunction();
        return null;
    }
}
