package org.luaj.vm2;


public final class Buffer {
	
	private static final int DEFAULT_CAPACITY = 64;
	
	private static final byte[] NOBYTES = {};

	private byte[] bytes;
	
	private int length;
	
	private int offset;
	
	private LuaValue value;
	
	public Buffer() {
		this(DEFAULT_CAPACITY);
	}
	
	public Buffer( int initialCapacity ) {
		bytes = new byte[ initialCapacity ];
		length = 0;
		offset = 0;
		value = null;
	}
	
	public Buffer(LuaValue value) {
		bytes = NOBYTES;
		length = offset = 0;
		this.value = value;
	}

	public LuaValue value() {
		return value != null? value: this.tostring();
	}

	public Buffer setvalue(LuaValue value) {
		bytes = NOBYTES;
		offset = length = 0;
		this.value = value;
		return this;
	}
	
	public final LuaString tostring() {
		realloc( length, 0 );
		return LuaString.valueOf( bytes, offset, length );
	}
	
	public String tojstring() {
		return value().tojstring();
	}
	
	public String toString() {
		return tojstring();
	}

	public final Buffer append( byte b ) {
		makeroom( 0, 1 );
		bytes[ offset + length++ ] = b;
		return this;
	}

	public final Buffer append(char c) {
		if ( (c) < 0x80 ) {
			makeroom( 0, 1 );
			bytes[ offset + length++ ] = (byte) c;
		} else if ( c < 0x800 ) {
			makeroom( 0, 2 );
			bytes[ offset + length++ ] = (byte) (0xC0 | ((c>>6)  & 0x1f));
			bytes[ offset + length++ ] = (byte) (0x80 | ( c      & 0x3f));
		} else {
			makeroom( 0, 3 );
			bytes[ offset + length++ ] = (byte) (0xE0 | ((c>>12) & 0x0f));
			bytes[ offset + length++ ] = (byte) (0x80 | ((c>>6)  & 0x3f));
			bytes[ offset + length++ ] = (byte) (0x80 | ( c      & 0x3f));
		}
		return this;
	}


	public final Buffer append( LuaValue val ) {
		append( val.strvalue() );
		return this;
	}
	
	public final Buffer append( LuaString str ) {
		final int n = str.m_length;
		makeroom( 0, n );
		str.copyInto( 0, bytes, offset + length, n );
		length += n;
		return this;
	}
	
	public final Buffer append( String str ) {
		char[] c = str.toCharArray();
		final int n = LuaString.lengthAsUtf8( c );
		makeroom( 0, n );
		LuaString.encodeToUtf8( c, c.length, bytes, offset + length );
		length += n;
		return this;
	}

	public Buffer concatTo(LuaValue lhs) {
		return setvalue(lhs.concat(value()));
	}

	public Buffer concatTo(LuaString lhs) {
		return value!=null&&!value.isstring()? setvalue(lhs.concat(value)): prepend(lhs);
	}

	public Buffer concatTo(LuaNumber lhs) {
		return value!=null&&!value.isstring()? setvalue(lhs.concat(value)): prepend(lhs.strvalue());
	}

	public Buffer prepend(LuaString s) {
		int n = s.m_length;
		makeroom( n, 0 );
		System.arraycopy( s.m_bytes, s.m_offset, bytes, offset-n, n );
		offset -= n;
		length += n;
		value = null;
		return this;
	}

	public final void makeroom( int nbefore, int nafter ) {
		if ( value != null ) {
			LuaString s = value.strvalue();
			value = null;
			length = s.m_length;
			offset = nbefore;
			bytes = new byte[nbefore+length+nafter];
			System.arraycopy(s.m_bytes, s.m_offset, bytes, offset, length);
		} else if ( offset+length+nafter > bytes.length || offset<nbefore ) {
			int n = nbefore+length+nafter;
			int m = n<32? 32: n<length*2? length*2: n;
			realloc( m, nbefore==0? 0: m-length-nafter );
		}
	}
	
	private final void realloc( int newSize, int newOffset ) {
		if ( newSize != bytes.length ) {
			byte[] newBytes = new byte[ newSize ];
			System.arraycopy( bytes, offset, newBytes, newOffset, length );
			bytes = newBytes;
			offset = newOffset;
		}
	}

}
