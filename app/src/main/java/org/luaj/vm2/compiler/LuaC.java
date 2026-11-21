package org.luaj.vm2.compiler;

import android.util.Log;

import com.myopicmobile.textwarrior.common.Flag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;


import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;


public class LuaC extends Constants {

	public static final LuaC instance = new LuaC();


    public static Prototype lexer(InputStream stream, String chunkname, Flag _abort) throws IOException {
		return (new CompileState()).luaY_parser2(stream, chunkname,_abort);
	}

	public static Prototype lexer(CharSequence stream, String chunkname, Flag _abort) throws IOException {
		return (new CompileState()).luaY_parser2(new ByteArrayInputStream(stream.toString().getBytes()), chunkname, _abort);
	}

	protected LuaC() {}

	public Prototype compile(InputStream stream, String chunkname) throws IOException {
		return (new CompileState()).luaY_parser(stream, chunkname);
	}


	static class CompileState {
		int nCcalls = 0;
		private Hashtable strings = new Hashtable();
		protected CompileState() {}
	
		private Prototype luaY_parser(InputStream z, String name) throws IOException{
			LexState lexstate = new LexState(this, z);
			FuncState funcstate = new FuncState();
			lexstate.fs = funcstate;
			lexstate.setinput(this, z.read(), z, (LuaString) LuaValue.valueOf(name) );
			funcstate.f = new Prototype();
			funcstate.f.source = (LuaString) LuaValue.valueOf(name);
			lexstate.mainfunc(funcstate);
			LuaC._assert (funcstate.prev == null);
			LuaC._assert (lexstate.dyd == null
					|| (lexstate.dyd.n_actvar == 0 && lexstate.dyd.n_gt == 0 && lexstate.dyd.n_label == 0));
			return funcstate.f;
		}

		private Prototype luaY_parser2(InputStream z, String name, Flag _abort) throws IOException{
			LexState lexstate = new LexState(this, z,true, _abort);
			FuncState funcstate = new FuncState();
			lexstate.fs = funcstate;
			lexstate.setinput(this, z.read(), z, (LuaString) LuaValue.valueOf(name) );
			funcstate.f = new Prototype();
			funcstate.f.source = (LuaString) LuaValue.valueOf(name);
			lexstate.mainfunc(funcstate);
			LuaC._assert (funcstate.prev == null);
			LuaC._assert (lexstate.dyd == null
					|| (lexstate.dyd.n_actvar == 0 && lexstate.dyd.n_gt == 0 && lexstate.dyd.n_label == 0));
			return funcstate.f;
		}

		public LuaString newTString(String s) {
			return cachedLuaString(LuaString.valueOf(s));
		}
	
		public LuaString newTString(LuaString s) {
			return cachedLuaString(s);
		}
	
		public LuaString cachedLuaString(LuaString s) {
			LuaString c = (LuaString) strings.get(s);
			if (c != null)
				return c;
			strings.put(s, s);
			return s;
		}
	
		public String pushfstring(String string) {
			return string;
		}
	}
}
