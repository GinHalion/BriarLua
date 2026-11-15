package org.luaj.vm2;


public final class UpValue {

	LuaValue[] array;
	int index;

	public UpValue( LuaValue[] stack, int index) {
		this.array = stack;
		this.index = index;
	}

	public String toString() {
		return index + "/" + array.length + " " + array[index];
	}

	public String tojstring() {
		return array[index].tojstring();
	}

	public final LuaValue getValue() {
		return array[index];
	}

	public final void setValue( LuaValue value ) {
		array[index] = value;
	}

	public final void close() {
		LuaValue[] old = array;
		array = new LuaValue[] { old[index] };
		old[index] = null;
		index = 0;
	}
}
