package com.myopicmobile.textwarrior.common;
import android.util.*;

public class TextWarriorException extends Exception {
	private static final boolean NDEBUG = false;
	private static final long serialVersionUID = -8393914265675038931L;

	public TextWarriorException(String msg){
		super(msg);
	}

	static public void fail(final String details){
		assertVerbose(false, details);
	}

	@SuppressWarnings("all")
	static public void assertVerbose(boolean condition, final String details){
		if(NDEBUG){
			return;
		}

		if (!condition){


			System.err.print("TextWarrior assertion failed: ");
			System.err.println(details);
			Log.i("lua",details);
		}
	}
}
