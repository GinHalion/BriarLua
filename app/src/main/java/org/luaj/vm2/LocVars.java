package org.luaj.vm2;

public class LocVars {
	public LuaString varname;
	
	public int startpc;
	
	public int endpc;
	public int startidx;
	public int endidx;
	public VarType type;

	public LocVars(LuaString varname, int startpc, int endpc) {
		this.varname = varname;
		this.startpc = startpc;
		this.endpc = endpc;
	}
	
	public String tojstring() {
		return varname+" "+startpc+"-"+endpc;
	}

	@Override
	public String toString() {
		return super.toString()+"("+tojstring()+" "+startidx+"-"+endidx+")";
	}
}
