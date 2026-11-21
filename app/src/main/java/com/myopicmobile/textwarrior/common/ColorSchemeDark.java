
package com.myopicmobile.textwarrior.common;


public class ColorSchemeDark extends ColorScheme {

	public ColorSchemeDark(){
		setColor(Colorable.FOREGROUND, OFF_WHITE);
		setColor(Colorable.BACKGROUND, OFF_BLACK);
		setColor(Colorable.NON_PRINTING_GLYPH, DARK_GREY);
	}

	private static final int BEIGE = 0xFFD7BA7D;
	private static final int DARK_GREY = 0xFF606060;
	private static final int FLUORESCENT_YELLOW = 0xFFEFF193;
	private static final int JUNGLE_GREEN = 0xFF608B4E;
	private static final int LIGHT_GREY = 0xFFD3D3D3;
	private static final int MARINE = 0xFF569CD6;
	private static final int OCEAN_BLUE = 0xFF256395;
	private static final int OFF_BLACK = 0xFF040404;
	private static final int OFF_WHITE = 0xFFD0D2D3;
	private static final int PEACH = 0xFFD69D85;

	@Override
	public boolean isDark() {
		return true;
	}
}
