package com.myopicmobile.textwarrior.android;

import com.myopicmobile.textwarrior.common.Language;

import android.view.KeyEvent;

public class KeysInterpreter {
	public static boolean isSwitchPanel(KeyEvent event){
		return (event.isShiftPressed() &&
					(event.getKeyCode() == KeyEvent.KEYCODE_ENTER));
	}

	public static char keyEventToPrintableChar(KeyEvent event){
		char c = Language.NULL_CHAR;

    	if (isNewline(event)){
    		c = Language.NEWLINE;
        }
    	else if (isBackspace(event)){
    		c = Language.BACKSPACE;
        }
    	else if (isTab(event)){
    		c = Language.TAB;
        }
    	else if (isSpace(event)){
    		c = ' ';
        }
    	else if (event.isPrintingKey()){
    		c = (char) event.getUnicodeChar(event.getMetaState());
    	}
    	
		return c;
	}
	
	private static boolean isTab(KeyEvent event){
		return (event.isShiftPressed() &&
					(event.getKeyCode() == KeyEvent.KEYCODE_SPACE)) ||
				(event.getKeyCode() == KeyEvent.KEYCODE_TAB);
	}

	private static boolean isBackspace(KeyEvent event) {
		return (event.getKeyCode() == KeyEvent.KEYCODE_DEL);
	}

	private static boolean isNewline(KeyEvent event) {
		return (event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
	}

	private static boolean isSpace(KeyEvent event) {
		return (event.getKeyCode() == KeyEvent.KEYCODE_SPACE);
	}

	public static boolean isNavigationKey(KeyEvent event) {
		int keyCode = event.getKeyCode();
		return keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
			keyCode == KeyEvent.KEYCODE_DPAD_UP ||
			keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
			keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
				keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
				keyCode == KeyEvent.KEYCODE_MOVE_END;
	}
}
