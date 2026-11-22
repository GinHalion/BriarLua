
package com.luajava;

public abstract class JavaFunction
{
	
	protected LuaState L;
	
	public abstract int execute() throws LuaException;
	
	public JavaFunction(LuaState L)
	{
		this.L = L;
	}

	public LuaObject getParam(int idx)
	{
		return L.getLuaObject(idx);
	}

	public void register(String name) throws LuaException
	{
	  synchronized (L)
	  {
			L.pushJavaFunction(this);
			L.setGlobal(name);
	  }
	}
}
