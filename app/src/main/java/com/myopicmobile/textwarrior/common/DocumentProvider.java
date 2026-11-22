package com.myopicmobile.textwarrior.common;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

public class DocumentProvider implements java.lang.CharSequence
{

	@Override
	public int length()
	{
		return _theText.length();
	}

	private int _currIndex;
	private final Document _theText;

	public DocumentProvider(Document.TextFieldMetrics metrics){
		_currIndex = 0;
		_theText = new Document(metrics);
	}

	public DocumentProvider(Document doc){
		_currIndex = 0;
		_theText = doc;
	}

	public DocumentProvider(DocumentProvider rhs){
		_currIndex = 0;
		_theText = rhs._theText;
	}

	public CharSequence subSequence(int charOffset, int maxChars){
		return _theText.subSequence(charOffset, maxChars);
	}

	public char charAt(int charOffset){
		if(_theText.isValid(charOffset)){
			return _theText.charAt(charOffset);
		}
		else{
			return Language.NULL_CHAR;
		}
	}

	public String getRow(int rowNumber){
		return _theText.getRow(rowNumber);
	}

	public int findRowNumber(int charOffset){
		return _theText.findRowNumber(charOffset);
	}

	public int findLineNumber(int charOffset){
		return _theText.findLineNumber(charOffset);
	}

	public int getRowOffset(int rowNumber){
		return _theText.getRowOffset(rowNumber);
	}


	public int getLineOffset(int lineNumber){
		return _theText.getLineOffset(lineNumber);
	}

	public int seekChar(int startingChar){
		if(_theText.isValid(startingChar)){
			_currIndex = startingChar;
		}
		else{
			_currIndex = -1;
		}
		return _currIndex;
	}

	public boolean hasNext(){
		return (_currIndex >= 0 &&
				_currIndex < _theText.getTextLength());
	}

	public char next(){
		char nextChar = _theText.charAt(_currIndex);
		++_currIndex;
		return nextChar;
	}

	public void insertBefore(char c, int insertionPoint, long timestamp){
		if(!_theText.isValid(insertionPoint)){
			return;
		}

		char[] a = new char[1];
		a[0] = c;
		_theText.insert(a, insertionPoint, timestamp, true);
	}

	public void insertBefore(char[] cArray, int insertionPoint, long timestamp){
		if(!_theText.isValid(insertionPoint) || cArray.length == 0){
			return;
		}

		_theText.insert(cArray, insertionPoint, timestamp, true);
	}

	public void insert(int i, CharSequence s)
	{
		_theText.insert(new char[]{s.charAt(0)},i,System.nanoTime(),true);
	}
	public void deleteAt(int deletionPoint, long timestamp){
		if(!_theText.isValid(deletionPoint)){
			return;
		}
		_theText.delete(deletionPoint, 1, timestamp, true);
	}


	public void deleteAt(int deletionPoint, int maxChars, long time){
		if(!_theText.isValid(deletionPoint) || maxChars <= 0){
			return;
		}
		int totalChars = Math.min(maxChars, _theText.getTextLength() - deletionPoint);
		_theText.delete(deletionPoint, totalChars, time, true);
	}

	public boolean isBatchEdit(){
		return _theText.isBatchEdit();
	}

	public void beginBatchEdit(){
		_theText.beginBatchEdit();
	}

	public void endBatchEdit(){
		_theText.endBatchEdit();
	}

	public int getRowCount(){
		return _theText.getRowCount();
	}

	public int getRowSize(int rowNumber){
		return _theText.getRowSize(rowNumber);
	}

	public int docLength(){
		return _theText.getTextLength();
	}

	public void clearSpans(){
		_theText.clearSpans();
	}

	public List<Pair> getSpans(){
		return _theText.getSpans();
	}

	public void setSpans(List<Pair> spans){
		_theText.setSpans(spans);
	}

	public void setMetrics(Document.TextFieldMetrics metrics){
		_theText.setMetrics(metrics);
	}

	public void setWordWrap(boolean enable){
		_theText.setWordWrap(enable);
	}

	public boolean isWordWrap(){
		return _theText.isWordWrap();
	}

	public void analyzeWordWrap(){
		_theText.analyzeWordWrap();
	}

	public boolean canUndo() {
		return _theText.canUndo();
	}

	public boolean canRedo() {
		return _theText.canRedo();
	}

	public int undo() {
		return _theText.undo();
	}

	public int redo() {
		return _theText.redo();
	}

	@Override
	public String toString()
	{
		return _theText.toString();
	}


	public void setLines(ArrayList<Rect> lines) {
		_theText.setLines(lines);
	}
	public ArrayList<Rect> getLines() {
		return _theText.getLines();
	}
}
