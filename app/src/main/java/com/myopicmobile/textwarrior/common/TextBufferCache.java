package com.myopicmobile.textwarrior.common;

public class TextBufferCache {
	private static final int CACHE_SIZE = 4;
	private Pair[] _cache = new Pair[CACHE_SIZE];

	public TextBufferCache(){
		_cache[0] = new Pair(0, 0);
		for (int i = 1; i < CACHE_SIZE; ++i){
			_cache[i] = new Pair(-1, -1);
		}
	}

	public Pair getNearestLine(int lineIndex){
		int nearestMatch = 0;
		int nearestDistance = Integer.MAX_VALUE;
		for(int i = 0; i < CACHE_SIZE; ++i){
			int distance = Math.abs(lineIndex - _cache[i].getFirst());
			if (distance < nearestDistance){
				nearestDistance = distance;
				nearestMatch = i;
			}
		}

		Pair nearestEntry = _cache[nearestMatch];
		makeHead(nearestMatch);
		return nearestEntry;
	}

	public Pair getNearestCharOffset(int charOffset){
		int nearestMatch = 0;
		int nearestDistance = Integer.MAX_VALUE;
		for(int i = 0; i < CACHE_SIZE; ++i){
			int distance = Math.abs(charOffset - _cache[i].getSecond());
			if (distance < nearestDistance){
				nearestDistance = distance;
				nearestMatch = i;
			}
		}

		Pair nearestEntry = _cache[nearestMatch];
		makeHead(nearestMatch);
		return nearestEntry;
	}

	private void makeHead(int newHead){
		if(newHead == 0){
			return;
		}

		Pair temp = _cache[newHead];
		for(int i = newHead; i > 1; --i){
			_cache[i] = _cache[i-1];
		}
		_cache[1] = temp;
	}

	public void updateEntry(int lineIndex, int charOffset){
		if(lineIndex <= 0){
			return;
		}

		if(!replaceEntry(lineIndex, charOffset)){
			insertEntry(lineIndex, charOffset);
		}
	}

	private boolean replaceEntry(int lineIndex, int charOffset){
		for (int i = 1; i < CACHE_SIZE; ++i){
			if(_cache[i].getFirst() == lineIndex){
				_cache[i].setSecond(charOffset);
				return true;
			}
		}
		return false;
	}

	private void insertEntry(int lineIndex, int charOffset){
		makeHead(CACHE_SIZE-1);
		_cache[1] = new Pair(lineIndex, charOffset);
	}

	final protected void invalidateCache(int fromCharOffset){
		for (int i = 1; i < CACHE_SIZE; ++i){
			if(_cache[i].getSecond() >= fromCharOffset){
				_cache[i] = new Pair(-1, -1);
			}
		}
	}
}
