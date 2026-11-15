package com.myopicmobile.textwarrior.common;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


public class TextBuffer implements java.lang.CharSequence
{

	private ArrayList<Rect> _lines;

	@Override
	public int length()
	{
		return getTextLength()-1;
	}

	protected final static int MIN_GAP_SIZE = 50;
	protected char[] _contents;
	protected int _gapStartIndex;
	protected int _gapEndIndex;
	protected int _lineCount;
	private int _allocMultiplier;
	private TextBufferCache _cache;
	private UndoStack _undoStack;

	protected List<Pair> _spans;


	public TextBuffer(){
		_contents = new char[MIN_GAP_SIZE + 1];
		_contents[MIN_GAP_SIZE] = Language.EOF;
		_allocMultiplier = 1;
		_gapStartIndex = 0;
		_gapEndIndex = MIN_GAP_SIZE;
		_lineCount = 1;
		_cache = new TextBufferCache();
		_undoStack = new UndoStack(this);
	}

	public static int memoryNeeded(int textSize){
		long bufferSize = textSize + MIN_GAP_SIZE + 1;
		if(bufferSize < Integer.MAX_VALUE){
			return (int) bufferSize;
		}
		return -1;
	}

	synchronized public void setBuffer(char[] newBuffer, int textSize, int lineCount){
		_contents = newBuffer;
		initGap(textSize);
		_lineCount = lineCount;
		_allocMultiplier = 1;
	}

	synchronized public void setBuffer(char[] newBuffer){
		int lineCount=1;
		int len=newBuffer.length;
		for(int i=0;i<len;i++){
			if(newBuffer[i]=='\n')
				lineCount++;
		}
		setBuffer(newBuffer,len,lineCount);
	}


	synchronized public String getLine(int lineNumber){
		int startIndex = getLineOffset(lineNumber);

		if(startIndex < 0){
			return new String();
		}
		int lineSize = getLineSize(lineNumber);

		return subSequence(startIndex, lineSize).toString();
	}

	synchronized public int getLineOffset(int lineNumber){
		if(lineNumber < 0){
			return -1;
		}

		Pair cachedEntry = _cache.getNearestLine(lineNumber);
		int cachedLine = cachedEntry.getFirst();
		int cachedOffset = cachedEntry.getSecond();

		int offset;
		if (lineNumber > cachedLine){
			offset = findCharOffset(lineNumber, cachedLine, cachedOffset);
		}
		else if (lineNumber < cachedLine){
			offset = findCharOffsetBackward(lineNumber, cachedLine, cachedOffset);
		}
		else{
			offset = cachedOffset;
		}

		if (offset >= 0){
			_cache.updateEntry(lineNumber, offset);
		}

		return offset;
	}

	private int findCharOffset(int targetLine, int startLine, int startOffset){
		int workingLine = startLine;
		int offset = logicalToRealIndex(startOffset);

		TextWarriorException.assertVerbose(isValid(startOffset),
			"findCharOffsetBackward: Invalid startingOffset given");

		while((workingLine < targetLine) && (offset < _contents.length)){
			if (_contents[offset] == Language.NEWLINE){
				++workingLine;
			}
			++offset;

			if(offset == _gapStartIndex){
				offset = _gapEndIndex;
			}
		}

		if (workingLine != targetLine){
			return -1;
		}
		return realToLogicalIndex(offset);
	}

	private int findCharOffsetBackward(int targetLine, int startLine, int startOffset){
		if (targetLine == 0){
			return 0;
		}

		TextWarriorException.assertVerbose(isValid(startOffset),
			"findCharOffsetBackward: Invalid startOffset given");

		int workingLine = startLine;
		int offset = logicalToRealIndex(startOffset);
		while(workingLine > (targetLine-1) && offset >= 0){
			if(offset == _gapEndIndex){
				offset = _gapStartIndex;
			}
			--offset;

			if (_contents[offset] == Language.NEWLINE){
				--workingLine;
			}

		}

		int charOffset;
		if (offset >= 0){
			charOffset = realToLogicalIndex(offset);
			++charOffset;
		}
		else{
			TextWarriorException.assertVerbose(false,
				"findCharOffsetBackward: Invalid cache entry or line arguments");
			charOffset = -1;
		}

		return charOffset;
	}

	synchronized public int findLineNumber(int charOffset){
		if(!isValid(charOffset)){
			return -1;
		}

		Pair cachedEntry = _cache.getNearestCharOffset(charOffset);
		int line = cachedEntry.getFirst();
		int offset = logicalToRealIndex(cachedEntry.getSecond());
		int targetOffset = logicalToRealIndex(charOffset);
		int lastKnownLine = -1;
		int lastKnownCharOffset = -1;

		if (targetOffset > offset){
			while((offset < targetOffset) && (offset < _contents.length)){
				if (_contents[offset] == Language.NEWLINE){
					++line;
					lastKnownLine = line;
					lastKnownCharOffset = realToLogicalIndex(offset) + 1;
				}

				++offset;
				if(offset == _gapStartIndex){
					offset = _gapEndIndex;
				}
			}
		}
		else if (targetOffset < offset){
			while((offset > targetOffset) && (offset > 0)){
				if(offset == _gapEndIndex){
					offset = _gapStartIndex;
				}
				--offset;

				if (_contents[offset] == Language.NEWLINE){
					lastKnownLine = line;
					lastKnownCharOffset = realToLogicalIndex(offset) + 1;
					--line;
				}
			}
		}


		if (offset == targetOffset){
			if(lastKnownLine != -1){
				_cache.updateEntry(lastKnownLine, lastKnownCharOffset);
			}
			return line;
		}
		else{
			return -1;
		}
	}


	synchronized public int getLineSize(int lineNumber){
		int lineLength = 0;
		int pos = getLineOffset(lineNumber);

		if (pos != -1){
			pos = logicalToRealIndex(pos);
			while(_contents[pos] != Language.NEWLINE &&
			 _contents[pos] != Language.EOF){
				++lineLength;
				++pos;

				if(pos == _gapStartIndex){
					pos = _gapEndIndex;
				}
			}
			++lineLength;
		}

		return lineLength;
	}

	synchronized public char charAt(int charOffset){
		return _contents[logicalToRealIndex(charOffset)];
	}

	synchronized public CharSequence subSequence(int charOffset, int maxChars){
		if(!isValid(charOffset) || maxChars <= 0){
			return new String();
		}
		int totalChars = maxChars;
		if((charOffset + totalChars) > getTextLength()){
			totalChars = getTextLength() - charOffset;
		}
		int realIndex = logicalToRealIndex(charOffset);
		char[] chars = new char[totalChars];

		for (int i = 0; i < totalChars; ++i){
			chars[i] = _contents[realIndex];
			++realIndex;
			if(realIndex == _gapStartIndex){
				realIndex = _gapEndIndex;
			}
		}

		return new String(chars);
	}

	char[] gapSubSequence(int charCount){
		char[] chars = new char[charCount];

		for (int i = 0; i < charCount; ++i){
			chars[i] = _contents[_gapStartIndex + i];
		}

		return chars;
	}

	public synchronized void insert(char[] c, int charOffset, long timestamp,
			boolean undoable){
		if(undoable){
			_undoStack.captureInsert(charOffset, c.length, timestamp);
		}

		int insertIndex = logicalToRealIndex(charOffset);

		if (insertIndex != _gapEndIndex){
			if (isBeforeGap(insertIndex)){
				shiftGapLeft(insertIndex);
			}
			else{
				shiftGapRight(insertIndex);
			}
		}

		if(c.length >= gapSize()){
			growBufferBy(c.length - gapSize());
		}

		for (int i = 0; i < c.length; ++i){
			if(c[i] == Language.NEWLINE){
				++_lineCount;
			}
			_contents[_gapStartIndex] = c[i];
			++_gapStartIndex;
		}

		_cache.invalidateCache(charOffset);

		onAdd(charOffset,c.length);
	}

	public synchronized void delete(int charOffset, int totalChars, long timestamp,
			boolean undoable){
		if(undoable){
			_undoStack.captureDelete(charOffset, totalChars, timestamp);
		}

		int newGapStart = charOffset + totalChars;

		if (newGapStart != _gapStartIndex){
			if (isBeforeGap(newGapStart)){
				shiftGapLeft(newGapStart);
			}
			else{
				shiftGapRight(newGapStart + gapSize());
			}
		}

		for(int i = 0; i < totalChars; ++i){
			--_gapStartIndex;
			if(_contents[_gapStartIndex] == Language.NEWLINE){
				--_lineCount;
			}
		}

		_cache.invalidateCache(charOffset);
		onDel(charOffset,totalChars);
	}

	private void onAdd(int charOffset,int totalChars){
		if(_spans==null||_spans.isEmpty())
			return;
		Pair s = findSpan(charOffset);
		Pair p=_spans.get(s.getFirst());
		p.setFirst(p.getFirst()+totalChars);
		if(_lines==null||_lines.isEmpty())
			return;
		for (Rect line : _lines) {
			if(line==null)
				continue;
			if(line.left>charOffset)
				line.left+=totalChars;
			if(line.right>charOffset)
				line.right+=totalChars;

		}
	}

	private void onDel(int charOffset,int totalChars){
		int len = length();
		if (len==0){
			clearSpans();
			return;
		}

		Pair s = findSpan2(charOffset);
		if(totalChars==1){
			Pair p=_spans.get(s.getFirst());
			if(p.getFirst()>1){
				p.setFirst(p.getFirst()-1);
			}
			else{
				_spans.remove(s.getFirst());
			}
		}
		else{
			int o=s.getSecond();
			int l=charOffset-o;
			Pair p=_spans.get(s.getFirst());
			if(p.getFirst()>l){
				p.setFirst(p.getFirst()-l);
			}
			else{
				_spans.remove(s.getFirst());
			}
			totalChars-=l;
			if(totalChars>0){
				for(int i=s.getFirst();i>=0;i--){
					Pair p1=_spans.get(i);
					l=p1.getFirst();
					if(totalChars>l){
						totalChars-=l;
						_spans.remove(i);
					}
					else{
						p1.setFirst(p1.getFirst()-totalChars);
						break;
					}
				}
			}
		}
		if(_lines==null||_lines.isEmpty())
			return;
		for (Rect line : _lines) {
			if(line==null)
				continue;
			if(line.left>charOffset)
				line.left-=totalChars;
			if(line.right>charOffset)
				line.right-=totalChars;

		}
	}

	private Pair findSpan(int index){
		int n=_spans.size();
		int cur=0;
		for (int i=0;i<n;i++){
			int l=_spans.get(i).getFirst();
			cur+=l;
			if(cur>=index)
				return new Pair(i,cur-l);
		}
		return new Pair(0,0);
	}

	private Pair findSpan2(int index){
		int n=_spans.size();
		int cur=0;
		for (int i=0;i<n;i++){
			int l=_spans.get(i).getFirst();
			cur+=l;
			if(cur>index)
				return new Pair(i,cur-l);
		}
		return new Pair(0,0);
	}



	synchronized void shiftGapStart(int displacement){
		if(displacement >= 0){
			onAdd(_gapStartIndex, displacement);
			_lineCount += countNewlines(_gapStartIndex, displacement);
		}
		else{
			onDel(_gapStartIndex, 0-displacement);
			_lineCount -= countNewlines(_gapStartIndex + displacement, -displacement);
		}

		_gapStartIndex += displacement;
		_cache.invalidateCache(realToLogicalIndex(_gapStartIndex - 1) + 1);
	}

	private int countNewlines(int start, int totalChars){
		int newlines = 0;
		for(int i = start; i < (start + totalChars); ++i){
			if(_contents[i] == Language.NEWLINE){
				++newlines;
			}
		}

		return newlines;
	}

	final protected void shiftGapLeft(int newGapStart){
		while(_gapStartIndex > newGapStart){
			--_gapEndIndex;
			--_gapStartIndex;
			_contents[_gapEndIndex] = _contents[_gapStartIndex];
		}
	}

	final protected void shiftGapRight(int newGapEnd){
		while(_gapEndIndex < newGapEnd){
			_contents[_gapStartIndex] = _contents[_gapEndIndex];
			++_gapStartIndex;
			++_gapEndIndex;
		}
	}

	protected void initGap(int contentsLength){
		int toPosition = _contents.length - 1;
		_contents[toPosition--] = Language.EOF;
		int fromPosition = contentsLength - 1;
		while(fromPosition >= 0){
			_contents[toPosition--] = _contents[fromPosition--];
		}
		_gapStartIndex = 0;
		_gapEndIndex = toPosition + 1;
	}

	protected void growBufferBy(int minIncrement){
		int increasedSize = minIncrement + MIN_GAP_SIZE * _allocMultiplier;
		char[] temp = new char[_contents.length + increasedSize];
		int i = 0;
		while(i < _gapStartIndex){
			temp[i] = _contents[i];
			++i;
		}

		i = _gapEndIndex;
		while(i < _contents.length){
			temp[i + increasedSize] = _contents[i];
			++i;
		}

		_gapEndIndex += increasedSize;
		_contents = temp;
		_allocMultiplier <<= 1;
	}

	final synchronized public int getTextLength(){
		return _contents.length - gapSize();
	}

	synchronized public int getLineCount(){
		return _lineCount;
	}

	final synchronized public boolean isValid(int charOffset){
		return (charOffset >= 0 && charOffset < getTextLength());
	}

	final protected int gapSize(){
		return _gapEndIndex - _gapStartIndex;
	}

	final protected int logicalToRealIndex(int i){
		if (isBeforeGap(i)){
			return i;
		}
		else{
			return i + gapSize();
		}
	}

	final protected int realToLogicalIndex(int i){
		if (isBeforeGap(i)){
			return i;
		}
		else{
			return i - gapSize();
		}
	}

	final protected boolean isBeforeGap(int i){
		return i < _gapStartIndex;
	}

	public void clearSpans(){
		_spans = new Vector<Pair>();
	    _spans.add(new Pair(length(), Lexer.NORMAL));
	}

	public List<Pair> getSpans(){
		return _spans;
	}

	public void setSpans(List<Pair> spans){
		_spans = spans;
	}

	public boolean isBatchEdit(){
		return _undoStack.isBatchEdit();
	}

	public void beginBatchEdit() {
		_undoStack.beginBatchEdit();
	}

	public void endBatchEdit() {
		_undoStack.endBatchEdit();
	}

	public boolean canUndo() {
		return _undoStack.canUndo();
	}

	public boolean canRedo() {
		return _undoStack.canRedo();
	}

	public int undo(){
		return _undoStack.undo();
	}

	public int redo(){
		return _undoStack.redo();
	}

	@Override
	public String toString()
	{
		int len=getTextLength();
		StringBuffer buf=new StringBuffer();
		for (int i=0;i < len;i++){
			char c=charAt(i);
			if (c==Language.EOF)
				break;
			buf.append(c);
		}
		return new String(buf);
	}


	public void setLines(ArrayList<Rect> lines) {
		_lines=lines;
	}

	public ArrayList<Rect> getLines() {
		return _lines;
	}
}
