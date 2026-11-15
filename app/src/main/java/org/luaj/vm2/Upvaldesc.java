package org.luaj.vm2;

public class Upvaldesc {

	public LuaString name;
	public VarType type;
	public final boolean instack;

	public final short idx;

	public Upvaldesc(LuaString name, boolean instack, int idx) {
		this.name = name;
		this.instack = instack;
		this.idx = (short) idx;
	}

	public String toString() {
		return idx + (instack? " instack ": " closed ") + String.valueOf(name);
	}
}
