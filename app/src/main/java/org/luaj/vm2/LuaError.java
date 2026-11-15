package org.luaj.vm2;


public class LuaError extends RuntimeException {
	private static final long serialVersionUID = 1L;

	protected int level;

	protected String fileline;

	protected String varname;

	protected String traceback;

	protected Throwable cause;

	private LuaValue object;

	public String getMessage() {
		if (traceback != null)
			return traceback;
		String m = super.getMessage();
		if (m == null)
			return null;
		if(varname!=null)
			m=m + " ( " +varname + " )";
		if (fileline != null)
			return fileline + " " + m;
		return m;
	}

	public LuaValue getMessageObject() {
		if (object != null) return object;
		String m = getMessage();
		return m != null ? LuaValue.valueOf(m): null;
	}

	public LuaError(Throwable cause) {
		super( "vm error: "+cause );
		this.cause = cause;
		this.level = 1;
	}

	public LuaError(String message) {
		super( message );
		this.level = 1;
	}

	public LuaError(String message, int level) {
		super( message );
		this.level = level;
	}

	public LuaError(LuaValue message_object) {
		super( message_object.tojstring() );
		this.object = message_object;
		this.level = 1;
	}


	public Throwable getCause() {
		return cause;
	}


}
