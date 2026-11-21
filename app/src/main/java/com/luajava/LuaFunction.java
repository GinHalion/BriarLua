package com.luajava;

public class LuaFunction <T extends java.lang.Object>extends LuaObject implements LuaMetaTable {

	@Override
	public T __call(Object[] arg) throws LuaException {
		return (T)super.call(arg);
	}

	@Override
	public Object __index(String key) {
		return null;
	}

	@Override
	public void __newIndex(String key, Object value) {
	}

	@Override
	public T call(Object...args) throws LuaException {
		Object ret = super.call(args);
		return (T)ret;
	}
	
	protected LuaFunction(LuaState L, String globalName) {
		super(L, globalName);
	}

	protected LuaFunction(LuaState L, int index) {
		super(L, index);
	}
}
