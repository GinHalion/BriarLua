package org.luaj.vm2;

public class LuaNil extends LuaValue {

	static final LuaNil _NIL = new LuaNil();

	public static LuaValue s_metatable;

	LuaNil() {}

	public int type() {
		return LuaValue.TNIL;
	}

	public String toString() {
		return "nil";
	}

	public String typename() {
		return "nil";
	}

	public String tojstring() {
		return "nil";
	}

	public LuaValue not()  {
		return LuaValue.TRUE;
	}

	public boolean toboolean() {
		return false;
	}

	public boolean isnil() {
		return true;
	}

	public LuaValue getmetatable() {
		return s_metatable;
	}

	public boolean equals(Object o) {
		return o instanceof LuaNil;
	}

	public LuaValue checknotnil() {
		return argerror("value");
	}

	public boolean isvalidkey() {
		return false;
	}

	public boolean     optboolean(boolean defval)          { return defval; }
	public double      optdouble(double defval)               { return defval; }
	public int         optint(int defval)                  { return defval; }
	public LuaInteger  optinteger(LuaInteger defval)       { return defval; }
	public long        optlong(long defval)                { return defval; }
	public LuaNumber   optnumber(LuaNumber defval)         { return defval; }
	public String      optjstring(String defval)            { return defval; }
	public LuaString   optstring(LuaString defval)         { return defval; }
	public Object      optuserdata(Object defval)          { return defval; }
	public Object      optuserdata(Class c, Object defval) { return defval; }
	public LuaValue    optvalue(LuaValue defval)           { return defval; }
}
