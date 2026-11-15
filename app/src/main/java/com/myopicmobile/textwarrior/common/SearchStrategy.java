package com.myopicmobile.textwarrior.common;

public  interface SearchStrategy {
	public int find(DocumentProvider src, String target, int start, int end,
			boolean isCaseSensitive, boolean isWholeWord);

	public int wrappedFind(DocumentProvider src, String target, int start,
			boolean isCaseSensitive, boolean isWholeWord);

	public int findBackwards(DocumentProvider src, String target, int start, int end,
			boolean isCaseSensitive, boolean isWholeWord);

	public int wrappedFindBackwards(DocumentProvider src, String target, int start,
			boolean isCaseSensitive, boolean isWholeWord);

	public Pair replaceAll(DocumentProvider src, String searchText,
			String replacementText, int mark,
			boolean isCaseSensitive, boolean isWholeWord);


	public int getProgress();
}
