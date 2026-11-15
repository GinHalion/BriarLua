package com.myopicmobile.textwarrior.common;

import java.util.LinkedList;

public class UndoStack {
	private TextBuffer _buf;
	private LinkedList<Command> _stack = new LinkedList<Command>();
	private boolean _isBatchEdit = false;
	private int _groupId = 0;
	private int _top = 0;
	long _lastEditTime = -1;

	public UndoStack(TextBuffer buf){
		_buf = buf;
	}

	public int undo(){
		if(canUndo()){
			Command lastUndone = _stack.get(_top-1);
			int group = lastUndone._group;
			do{
				Command c = _stack.get(_top-1);
				if(c._group != group){
					break;
				}

				lastUndone = c;
				c.undo();
				--_top;
			}
			while(canUndo());

			return lastUndone.findUndoPosition();
		}

		return -1;
	}

	public int redo(){
		if(canRedo()){
			Command lastRedone = _stack.get(_top);
			int group = lastRedone._group;
			do{
				Command c = _stack.get(_top);
				if(c._group != group){
					break;
				}

				lastRedone = c;
				c.redo();
				++_top;
			}
			while(canRedo());

			return lastRedone.findRedoPosition();
		}

		return -1;
	}

	public void captureInsert(int start, int length, long time){
		boolean mergeSuccess = false;

		if(canUndo()){
			Command c = _stack.get(_top - 1);

			if(c instanceof InsertCommand
					&& c.merge(start, length, time)){
				mergeSuccess = true;
			}
			else{
				c.recordData();
			}
		}

		if(!mergeSuccess){
			push(new InsertCommand(start, length, _groupId));

			if(!_isBatchEdit){
				_groupId++;
			}
		}

		_lastEditTime = time;
	}

	public void captureDelete(int start, int length, long time){
		boolean mergeSuccess = false;

		if(canUndo()){
			Command c = _stack.get(_top - 1);

			if(c instanceof DeleteCommand
					&& c.merge(start, length, time)){
				mergeSuccess = true;
			}
			else{
				c.recordData();
			}
		}

		if(!mergeSuccess){
			push(new DeleteCommand(start, length, _groupId));

			if(!_isBatchEdit){
				_groupId++;
			}
		}

		_lastEditTime = time;
	}

	private void push(Command c){
		trimStack();
		++_top;
		_stack.add(c);
	}

	private void trimStack(){
		while(_stack.size() > _top){
			_stack.removeLast();
		}
	}

	public final boolean canUndo(){
		return _top > 0;
	}

	public final boolean canRedo(){
		return _top < _stack.size();
	}

	public boolean isBatchEdit(){
		return _isBatchEdit;
	}

	public void beginBatchEdit(){
		_isBatchEdit = true;
	}

	public void endBatchEdit(){
		_isBatchEdit = false;
		_groupId++;
	}



	private abstract class Command{
		public final static long MERGE_TIME = 1000000000;
		public int _start;
		public int _length;
		public String _data;
		public int _group;

		public abstract void undo();
		public abstract void redo();
		public abstract void recordData();
		public abstract int findUndoPosition();
		public abstract int findRedoPosition();

		public abstract boolean merge(int start, int length, long time);
	}

	private class InsertCommand extends Command{
		public InsertCommand(int start, int length, int groupNumber){
			_start = start;
			_length = length;
			_group = groupNumber;
		}

		@Override
		public boolean merge(int newStart, int length, long time) {
			if(_lastEditTime < 0){
				return false;
			}

			if((time - _lastEditTime) < MERGE_TIME
					&& newStart == _start + _length){
				_length += length;
				trimStack();
				return true;
			}

			return false;
		}

		@Override
		public void recordData() {
			_data = _buf.subSequence(_start, _length).toString();
		}

		@Override
		public void undo() {
			if(_data == null){
				recordData();
				_buf.shiftGapStart(-_length);
			}
			else{
				_buf.delete(_start, _length, 0 ,false);
			}
		}

		@Override
		public void redo() {
			_buf.insert(_data.toCharArray(), _start, 0, false);
		}

		@Override
		public int findRedoPosition() {
			return _start + _length;
		}

		@Override
		public int findUndoPosition() {
			return _start;
		}
	}


	private class DeleteCommand extends Command{
		public DeleteCommand(int start, int length, int seqNumber){
			_start = start;
			_length = length;
			_group = seqNumber;
		}

		@Override
		public boolean merge(int newStart, int length, long time) {
			if(_lastEditTime < 0){
				return false;
			}

			if((time - _lastEditTime) < MERGE_TIME
					&& newStart == _start - _length - length + 1){
				_start = newStart;
				_length += length;
				trimStack();
				return true;
			}

			return false;
		}

		@Override
		public void recordData() {
			_data = new String(_buf.gapSubSequence(_length));
		}

		@Override
		public void undo() {
			if(_data == null){
				recordData();
				_buf.shiftGapStart(_length);
			}
			else{
				_buf.insert(_data.toCharArray(), _start, 0, false);
			}
		}

		@Override
		public void redo() {
			_buf.delete(_start, _length, 0, false);
		}

		@Override
		public int findRedoPosition() {
			return _start;
		}

		@Override
		public int findUndoPosition() {
			return _start + _length;
		}
	}
}
