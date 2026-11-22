package org.luaj.vm2;


public class Prototype {
	public LuaValue[] k;
	public int[] code;
	public Prototype[] p;
	public int[] lineinfo;
	public LocVars[] locvars;
	public Upvaldesc[] upvalues;
	public LuaString  source;
	public int linedefined;
	public int lastlinedefined;
	public int numparams;
	public int is_vararg;
	public int maxstacksize;
	private static final Upvaldesc[] NOUPVALUES = {};
	private static final Prototype[] NOSUBPROTOS = {};
	public int endidx;
	public int startidx;
	public String name;

    public Prototype() {
		p = NOSUBPROTOS;
		upvalues = NOUPVALUES;
	}

	public Prototype(int n_upvalues) {
		p = NOSUBPROTOS;
		upvalues = new Upvaldesc[n_upvalues];
	}
	
	public String toString() {
		return source + ":" + linedefined+"-"+lastlinedefined;
	}
	
	public LuaString getlocalname(int number, int pc) {
	  int i;
	  for (i = 0; i<locvars.length && locvars[i].startpc <= pc; i++) {
	    if (pc < locvars[i].endpc) {
	    	number--;
	      if (number == 0)
	        return locvars[i].varname;
	    }
	  }
	  return null;
	}
	
	public String shortsource() {
		if(source==null)
			return "";
		String name = source.tojstring();
        if ( name.startsWith("@") || name.startsWith("=") )
			name = name.substring(1);
		else if ( name.startsWith("\033") )
			name = "binary string";
        return name;
	}
}
