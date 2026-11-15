
package com.luajava;

public class LuaException extends Exception
{
	private static final long serialVersionUID = 1L;

	public LuaException(String str)
	{
		super(str);
	}

	public LuaException(Exception e)
	{
	   super((e.getCause() != null) ? e.getCause() : e);
	}
}
