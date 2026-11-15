
package com.luajava;

import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class LuaObject implements Serializable {
	protected int ref;

	protected final LuaState L;


	protected LuaObject(LuaState L) {
		this.L = L;
	}
	protected LuaObject(LuaState L, String globalName) {
		synchronized (L) {
			this.L = L;
			L.getGlobal(globalName);
			registerValue(-1);
			L.pop(1);
		}
	}

	protected LuaObject(LuaObject parent, String name) throws LuaException {
		synchronized (parent.getLuaState()) {
			this.L = parent.getLuaState();

			if (!parent.isTable() && !parent.isUserdata()) {
				throw new LuaException("Object parent should be a table or userdata .");
			}

			parent.push();
			L.pushString(name);
			L.getTable(-2);
			L.remove(-2);
			registerValue(-1);
			L.pop(1);
		}
	}

	protected LuaObject(LuaObject parent, Number name) throws LuaException {
		synchronized (parent.getLuaState()) {
			this.L = parent.getLuaState();
			if (!parent.isTable() && !parent.isUserdata())
				throw new LuaException("Object parent should be a table or userdata .");

			parent.push();
			L.pushNumber(name.doubleValue());
			L.getTable(-2);
			L.remove(-2);
			registerValue(-1);
			L.pop(1);
		}
	}

	protected LuaObject(LuaObject parent, LuaObject name) throws LuaException {
		if (parent.getLuaState() != name.getLuaState())
			throw new LuaException("LuaStates must be the same!");
		synchronized (parent.getLuaState()) {
			if (!parent.isTable() && !parent.isUserdata())
				throw new LuaException("Object parent should be a table or userdata .");

			this.L = parent.getLuaState();

			parent.push();
			name.push();
			L.getTable(-2);
			L.remove(-2);
			registerValue(-1);
			L.pop(1);
		}
	}

	protected LuaObject(LuaState L, int index) {
		synchronized (L) {
			this.L = L;

			registerValue(index);
		}
	}

	public LuaState getLuaState() {
		return L;
	}

	protected void registerValue(int index) {
		synchronized (L) {
			L.pushValue(index);
			int key;ref = L.Lref(LuaState.LUA_REGISTRYINDEX);
		}
	}

	@Override
	protected void finalize() {
		Log.i("luaObject", "finalize: "+ref+";"+toString());
		try {
			synchronized (L) {
				if (L.getPointer() != 0)
					L.LunRef(LuaState.LUA_REGISTRYINDEX, ref);
			}
		}
		catch (Exception e) {
			System.err.println("Unable to release object " + ref);
		}
	}

	public void push() {
		L.rawGetI(LuaState.LUA_REGISTRYINDEX, ref);
	}

	public void pop() {
		L.pop(1);
	}

	public boolean isNil() {
		synchronized (L) {
			push();
			boolean bool = L.isNil(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isBoolean() {
		synchronized (L) {
			push();
			boolean bool = L.isBoolean(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isNumber() {
		synchronized (L) {
			push();
			boolean bool = L.isNumber(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isInteger() {
		synchronized (L) {
			push();
			boolean bool = L.isInteger(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isString() {
		synchronized (L) {
			push();
			boolean bool = L.isString(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isFunction() {
		synchronized (L) {
			push();
			boolean bool = L.isFunction(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isJavaObject() {
		synchronized (L) {
			push();
			boolean bool = L.isObject(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isJavaFunction() {
		synchronized (L) {
			push();
			boolean bool = L.isJavaFunction(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isTable() {
		synchronized (L) {
			push();
			boolean bool = L.isTable(-1);
			L.pop(1);
			return bool;
		}
	}

	public boolean isUserdata() {
		synchronized (L) {
			push();
			boolean bool = L.isUserdata(-1);
			L.pop(1);
			return bool;
		}
	}

	public int type() {
		synchronized (L) {
			push();
			int type = L.type(-1);
			L.pop(1);
			return type;
		}
	}

	public boolean getBoolean() {
		synchronized (L) {
			push();
			boolean bool = L.toBoolean(-1);
			L.pop(1);
			return bool;
		}
	}

	public double getNumber() {
		synchronized (L) {
			push();
			double db = L.toNumber(-1);
			L.pop(1);
			return db;
		}
	}

	public long getInteger() {
		synchronized (L) {
			push();
			long lg = L.toInteger(-1);
			L.pop(1);
			return lg;
		}
	}

	public String getString() {
		synchronized (L) {
			push();
			String str = L.toString(-1);
			L.pop(1);
			return str;
		}
	}

	public LuaTable<?,?> getTable() {
		synchronized (L) {
			push();
			LuaTable td = new LuaTable(L, -1);
			L.pop(1);
			return td;
		}
	}

	public LuaFunction<?> getFunction() {
		synchronized (L) {
			push();
			LuaFunction<?> ft = new LuaFunction(L, -1);
			L.pop(1);
			return ft;
		}
	}


	public Object getObject() throws LuaException {
		synchronized (L) {
			push();
			Object obj = L.getObjectFromUserdata(-1);
			L.pop(1);
			return obj;
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getObject(Class<T> type) {
		push();
		T obj=null;
		try {
			obj=(T)LuaJavaAPI.compareTypes(L,type,-1);
		}
		catch (LuaException e) {}
		L.pop(1);
		return obj;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(Object key, Class<T> type) {
		push();
		T obj=null;
		try {
			L.pushObjectValue(key);
			L.getTable(-2);
			obj=(T)LuaJavaAPI.compareTypes(L,type,-1);
			L.pop(1);
		}
		catch (LuaException e) {e.printStackTrace();}
	    L.pop(1);
		if(obj == null){
			if(Number.class.isAssignableFrom(type))
				obj=(T)Integer.valueOf(0);
			if(Boolean.class.isAssignableFrom(type))
				obj=(T)Boolean.valueOf(false);
		}
		return obj;
	}

	public LuaObject getField(String field) throws LuaException {
		return L.getLuaObject(this, field);
	}

	public void setField(String field, Object obj) {
		push();
		try {
			L.pushObjectValue(obj);
		}
		catch (LuaException e) {
			L.pushNil();
		}

		L.setField(-2, field);
		L.pop(1);
	}

	public LuaObject getI(long idx) throws LuaException {
		return L.getLuaObject(this, idx);
	}

	public void setI(long idx, Object obj) {
		push();
		try {
			L.pushObjectValue(obj);
		}
		catch (LuaException e) {
			L.pushNil();
		}
		L.setI(-2, idx);
		L.pop(1);
	}

	public Object[] call_aux(Object[] args, int nres) throws LuaException {
		synchronized (L) {
			if (!isFunction() && !isTable() && !isUserdata())
				throw new LuaException("Invalid object. Not a function, table or userdata .");

			int top = L.getTop();
			push();
			int nargs;
			if (args != null) {
				nargs = args.length;
				for (int i = 0; i < nargs; i++) {
					Object obj = args[i];
					L.pushObjectValue(obj);
				}
			}
			else
				nargs = 0;

			int err = L.pcall(nargs, nres, 0);

			if (err != 0) {
				String str;
				if (L.isString(-1)) {
					str = L.toString(-1);
					L.pop(1);
				}
				else
					str = "";

				if (err == LuaState.LUA_ERRRUN) {
					str = "Runtime error. " + str;
				}
				else if (err == LuaState.LUA_ERRMEM) {
					str = "Memory allocation error. " + str;
				}
				else if (err == LuaState.LUA_ERRERR) {
					str = "Error while running the error handler function. " + str;
				}
				else {
					str = "Lua Error code " + err + ". " + str;
				}

				throw new LuaException(str);
			}

			if (nres == LuaState.LUA_MULTRET)
				nres = L.getTop() - top;
			if (L.getTop() - top < nres) {
				throw new LuaException("Invalid Number of Results .");
			}

			Object[] res = new Object[nres];

			for (int i = nres; i > 0; i--) {
				res[i - 1] = L.toJavaObject(-1);
				L.pop(1);
			}
			return res;
		}
	}

	public Object call(Object... args) throws LuaException {
		return call_aux(args, 1)[0];
	}

	public LuaObject[] _call_aux(Object[] args, int nres) throws LuaException {
		synchronized (L) {
			if (!isFunction() && !isTable() && !isUserdata())
				throw new LuaException("Invalid object. Not a function, table or userdata .");

			int top = L.getTop();
			push();
			int nargs;
			if (args != null) {
				nargs = args.length;
				for (int i = 0; i < nargs; i++) {
					Object obj = args[i];
					L.pushObjectValue(obj);
				}
			}
			else
				nargs = 0;

			int err = L.pcall(nargs, nres, 0);

			if (err != 0) {
				String str;
				if (L.isString(-1)) {
					str = L.toString(-1);
					L.pop(1);
				}
				else
					str = "";

				if (err == LuaState.LUA_ERRRUN) {
					str = "Runtime error. " + str;
				}
				else if (err == LuaState.LUA_ERRMEM) {
					str = "Memory allocation error. " + str;
				}
				else if (err == LuaState.LUA_ERRERR) {
					str = "Error while running the error handler function. " + str;
				}
				else {
					str = "Lua Error code " + err + ". " + str;
				}

				throw new LuaException(str);
			}

			if (nres == LuaState.LUA_MULTRET)
				nres = L.getTop() - top;
			if (L.getTop() - top < nres) {
				throw new LuaException("Invalid Number of Results .");
			}

			LuaObject[] res = new LuaObject[nres];

			for (int i = nres; i > 0; i--) {
				res[i - 1] = L.getLuaObject(-1);
				L.pop(1);
			}
			return res;
		}
	}

	public LuaObject _call(Object... args) throws LuaException {
		return _call_aux(args, 1)[0];
	}

	public byte[] dump() throws LuaException {
		synchronized (L) {
			if (!isFunction())
				throw new LuaException("Invalid object. Not a function .");

			push();
			byte[] buf=L.dump(-1);
			L.pop(1);
			return buf;
		}
	}

	public Object[] asArray() throws IllegalArgumentException, ArrayIndexOutOfBoundsException, LuaException {
		synchronized (L) {
			if (!isTable())
				throw new LuaException("Invalid object. Not a table .");
			push();
			int n = L.objLen(-1);
			Object array = Array.newInstance(Object.class, n);
			for (int i = 1;i <= n;i++) {
				L.pushInteger(i);
				L.getTable(-2);
				try {
					Array.set(array, i - 1, L.toJavaObject(-1));
				}
				catch (LuaException e) {}
				L.pop(1);
			}
			L.pop(1);
			return (Object[])array;
		}
	}

	public Map asMap(LuaState L, Class<?> clazz, int idx) throws LuaException {
		synchronized (L) {
			if (!isTable())
				throw new LuaException("Invalid object. Not a table .");
			HashMap<Object,Object> map = new HashMap<Object, Object>();
			push();
			L.pushNil();
			while (L.next(idx) != 0) {
				try {
					map.put(L.toJavaObject(-2), L.toJavaObject(-1));
				}
				catch (LuaException e) {}
				L.pop(1);
			}
			L.pop(1);
			return map;
		}
	}

	public String toString() {
		synchronized (L) {
			try {
				if (isNil())
					return "nil";
				else if (isBoolean())
					return String.valueOf(getBoolean());
				else if (isNumber())
					return String.valueOf(getNumber());
				else if (isString())
					return getString();
				else if (isFunction())
					return "Lua Function";
				else if (isJavaObject())
					return getObject().toString();
				else if (isUserdata())
					return "Userdata";
				else if (isTable())
					return "Lua Table";
				else if (isJavaFunction())
					return "Java Function";
				else
					return null;
			}
			catch (LuaException e) {
				return null;
			}
		}
	}

	public Object createProxy(String implem) throws ClassNotFoundException, LuaException {
		synchronized (L) {
			if (!isTable())
				throw new LuaException("Invalid Object. Must be Table.");

			StringTokenizer st = new StringTokenizer(implem, ",");
			Class[] interfaces = new Class[st.countTokens()];
			for (int i = 0; st.hasMoreTokens(); i++)
				interfaces[i] = Class.forName(st.nextToken());

			InvocationHandler handler = new LuaInvocationHandler(this);

			return Proxy.newProxyInstance(this.getClass().getClassLoader(), interfaces, handler);
		}
	}

	public Object createProxy(Class implem) throws LuaException {
		synchronized (L) {
			if (!isTable() && !isFunction())
				throw new LuaException("Invalid Object. Must be Table or Function.");

			if(isFunction() && implem.getMethods().length!=1)
				throw new LuaException("Invalid Object. Must be a interface Method of Function.");
            if(isTable()&&getTable().isList()){
	            throw new LuaException("Invalid Object. Must be Table is Not Array.");
            }
			Class[] interfaces = new Class[]{implem};

			InvocationHandler handler = new LuaInvocationHandler(this);

			return Proxy.newProxyInstance(implem.getClassLoader(), interfaces, handler);
		}
	}

}
