
package com.myopicmobile.textwarrior.common;


public class ColorSchemeLight extends ColorScheme {

	public ColorSchemeLight(){
		setColor(Colorable.FOREGROUND, OFF_BLACK);
		setColor(Colorable.BACKGROUND, OFF_WHITE);
		setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE);
		setColor(Colorable.CARET_FOREGROUND, OFF_WHITE);
	}

	private static final int OFF_WHITE = 0xFFF0F0ED;
	private static final int OFF_BLACK = 0xFF333333;

	@Override
	public boolean isDark() {
		return false;
	}
}
