package org.luaj.vm2;

abstract
public class LuaNumber extends LuaValue {

	public static LuaValue s_metatable;

	public int type() {
		return TNUMBER;
	}

	public String typename() {
		return "number";
	}

	public LuaNumber checknumber() {
		return this;
	}

	public LuaNumber checknumber(String errmsg) {
		return this;
	}

	public LuaNumber optnumber(LuaNumber defval) {
		return this;
	}

	public LuaValue tonumber() {
		return this;
	}

	public boolean isnumber() {
		return true;
	}

	public boolean isstring() {
		return true;
	}

	public LuaValue getmetatable() {
		return s_metatable;
	}

	public LuaValue concat(LuaValue rhs)      { return rhs.concatTo(this); }
	public Buffer   concat(Buffer rhs)        { return rhs.concatTo(this); }
	public LuaValue concatTo(LuaNumber lhs)   { return strvalue().concatTo(lhs.strvalue()); }
	public LuaValue concatTo(LuaString lhs)   { return strvalue().concatTo(lhs); }

}
