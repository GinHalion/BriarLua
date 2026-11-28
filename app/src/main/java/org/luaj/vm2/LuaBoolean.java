package org.luaj.vm2;

public final class LuaBoolean extends LuaValue {

	static final LuaBoolean _TRUE = new LuaBoolean(true);
	
	static final LuaBoolean _FALSE = new LuaBoolean(false);
	
	public static LuaValue s_metatable;

	public final boolean v;

	LuaBoolean(boolean b) {
		this.v = b;
	}

	public int type() {
		return LuaValue.TBOOLEAN;
	}

	public String typename() {
		return "boolean";
	}

	public boolean isboolean() {
		return true;
	}

	public LuaValue not() {
		return v ? FALSE : LuaValue.TRUE;
	}

	public boolean booleanValue() {
		return v;
	}

	public boolean toboolean() {
		return v;
	}

	public String tojstring() {
		return v ? "true" : "false";
	}

	public boolean optboolean(boolean defval) {
		return this.v;
	}
	
	public boolean checkboolean() {
		return v;
	}
	
	public LuaValue getmetatable() {
		return s_metatable;
	}
}
