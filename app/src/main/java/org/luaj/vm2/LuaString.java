package org.luaj.vm2;


import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class LuaString extends LuaValue {

	public static LuaValue s_metatable;

	public final byte[] m_bytes;

	public final int m_offset;

	public final int m_length;

	private final int m_hashcode;

	static final int RECENT_STRINGS_CACHE_SIZE = 128;

	static final int RECENT_STRINGS_MAX_LENGTH = 32;
	private char[] m_chars;
	private String m_string;

	public char[] toCharArray() {
		if(m_chars==null)
		    m_chars=toCharAsUtf8(m_bytes,m_offset,m_length);
		return m_chars;
	}

	private static final class RecentShortStrings {
		private static final LuaString recent_short_strings[] =
				new LuaString[RECENT_STRINGS_CACHE_SIZE];
	}

	public static LuaString valueOf(String string) {
		char[] c = string.toCharArray();
		byte[] b = new byte[lengthAsUtf8(c)];
		encodeToUtf8(c, c.length, b, 0);
		return valueUsing(b, 0, b.length);
	}

	public static LuaString valueOf(byte[] bytes, int off, int len) {
		if (len > RECENT_STRINGS_MAX_LENGTH)
			return valueFromCopy(bytes, off, len);
		final int hash = hashCode(bytes, off, len);
		final int bucket = hash & (RECENT_STRINGS_CACHE_SIZE - 1);
		final LuaString t = RecentShortStrings.recent_short_strings[bucket];
		if (t != null && t.m_hashcode == hash && t.byteseq(bytes, off, len)) return t;
		final LuaString s = valueFromCopy(bytes, off, len);
		RecentShortStrings.recent_short_strings[bucket] = s;
		return s;
	}

	private static LuaString valueFromCopy(byte[] bytes, int off, int len) {
		final byte[] copy = new byte[len];
		System.arraycopy(bytes, off, copy, 0, len);
		return new LuaString(copy, 0, len);
	}

	static public LuaString valueUsing(byte[] bytes, int off, int len) {
		if (bytes.length > RECENT_STRINGS_MAX_LENGTH)
			return new LuaString(bytes, off, len);
		final int hash = hashCode(bytes, off, len);
		final int bucket = hash & (RECENT_STRINGS_CACHE_SIZE - 1);
		final LuaString t = RecentShortStrings.recent_short_strings[bucket];
		if (t != null && t.m_hashcode == hash && t.byteseq(bytes, off, len)) return t;
		final LuaString s = new LuaString(bytes, off, len);
		RecentShortStrings.recent_short_strings[bucket] = s;
		return s;
	}

	public static LuaString valueOf(char[] bytes) {
		return valueOf(bytes, 0, bytes.length);
	}

	public static LuaString valueOf(char[] bytes, int off, int len) {
		byte[] b = new byte[len];
		for ( int i=0; i<len; i++ )
			b[i] = (byte) bytes[i + off];
		return valueUsing(b, 0, len);
	}

	public static LuaString valueOf(byte[] bytes) {
		return valueOf(bytes, 0, bytes.length);
	}

	public static LuaString valueUsing(byte[] bytes) {
		return valueUsing(bytes, 0, bytes.length);
	}

	private LuaString(byte[] bytes, int offset, int length) {
		this.m_bytes = bytes;
		this.m_offset = offset;
		this.m_length = length;
		this.m_hashcode = hashCode(bytes, offset, length);
	}

	public boolean isstring() {
		return true;
	}

	public LuaValue getmetatable() {
		return s_metatable;
	}

	public int type() {
		return LuaValue.TSTRING;
	}

	public String typename() {
		return "string";
	}

	public String tojstring() {
		if(m_string==null)
		    m_string = decodeAsUtf8(m_bytes, m_offset, m_length);
		return m_string;
	}

	public int lengthAsUtf8(){
		int i,j,n,b;
		for ( i=m_offset,j=m_offset+m_length,n=0; i<j; ++n ) {
			switch (0xE0 & m_bytes[i++]) {
				case 0xE0:
					++i;
				case 0xC0:
					++i;
			}
		}
		return n;
	}

	public LuaValue neg() { double d = scannumber(); return Double.isNaN(d)? super.neg(): valueOf(-d); }

	public LuaValue   add( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(ADD,rhs): rhs.add(d); }
	public LuaValue   add( double rhs )        { return valueOf( checkarith() + rhs ); }
	public LuaValue   add( int rhs )           { return valueOf( checkarith() + rhs ); }
	public LuaValue   sub( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(SUB,rhs): rhs.subFrom(d); }
	public LuaValue   sub( double rhs )        { return valueOf( checkarith() - rhs ); }
	public LuaValue   sub( int rhs )           { return valueOf( checkarith() - rhs ); }
	public LuaValue   subFrom( double lhs )    { return valueOf( lhs - checkarith() ); }
	public LuaValue   mul( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(MUL,rhs): rhs.mul(d); }
	public LuaValue   mul( double rhs )        { return valueOf( checkarith() * rhs ); }
	public LuaValue   mul( int rhs )           { return valueOf( checkarith() * rhs ); }
	public LuaValue   pow( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(POW,rhs): rhs.powWith(d); }
	public LuaValue   pow( double rhs )        { return LuaNumber.valueOf(Math.pow(checkarith(),rhs)); }
	public LuaValue   pow( int rhs )           { return LuaNumber.valueOf(Math.pow(checkarith(),rhs)); }
	public LuaValue   powWith( double lhs )    { return LuaNumber.valueOf(Math.pow(lhs, checkarith())); }
	public LuaValue   powWith( int lhs )       { return LuaNumber.valueOf(Math.pow(lhs, checkarith())); }
	public LuaValue   div( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(DIV,rhs): rhs.divInto(d); }
	public LuaValue   div( double rhs )        { return LuaDouble.ddiv(checkarith(),rhs); }
	public LuaValue   div( int rhs )           { return LuaDouble.ddiv(checkarith(),rhs); }
	public LuaValue   divInto( double lhs )    { return LuaDouble.ddiv(lhs, checkarith()); }
	public LuaValue   mod( LuaValue rhs )      { double d = scannumber(); return Double.isNaN(d)? arithmt(MOD,rhs): rhs.modFrom(d); }
	public LuaValue   mod( double rhs )        { return LuaDouble.dmod(checkarith(), rhs); }
	public LuaValue   mod( int rhs )           { return LuaDouble.dmod(checkarith(), rhs); }
	public LuaValue   modFrom( double lhs )    { return LuaDouble.dmod(lhs, checkarith()); }

	public LuaValue   lt( LuaValue rhs )         { return rhs.isstring() ? (rhs.strcmp(this)>0? LuaValue.TRUE: FALSE) : super.lt(rhs); }
	public boolean lt_b( LuaValue rhs )       { return rhs.isstring() ? rhs.strcmp(this)>0 : super.lt_b(rhs); }
	public boolean lt_b( int rhs )         { typerror("attempt to compare string with number"); return false; }
	public boolean lt_b( double rhs )      { typerror("attempt to compare string with number"); return false; }
	public LuaValue   lteq( LuaValue rhs )       { return rhs.isstring() ? (rhs.strcmp(this)>=0? LuaValue.TRUE: FALSE) : super.lteq(rhs); }
	public boolean lteq_b( LuaValue rhs )     { return rhs.isstring() ? rhs.strcmp(this)>=0 : super.lteq_b(rhs); }
	public boolean lteq_b( int rhs )       { typerror("attempt to compare string with number"); return false; }
	public boolean lteq_b( double rhs )    { typerror("attempt to compare string with number"); return false; }
	public LuaValue   gt( LuaValue rhs )         { return rhs.isstring() ? (rhs.strcmp(this)<0? LuaValue.TRUE: FALSE) : super.gt(rhs); }
	public boolean gt_b( LuaValue rhs )       { return rhs.isstring() ? rhs.strcmp(this)<0 : super.gt_b(rhs); }
	public boolean gt_b( int rhs )         { typerror("attempt to compare string with number"); return false; }
	public boolean gt_b( double rhs )      { typerror("attempt to compare string with number"); return false; }
	public LuaValue   gteq( LuaValue rhs )       { return rhs.isstring() ? (rhs.strcmp(this)<=0? LuaValue.TRUE: FALSE) : super.gteq(rhs); }
	public boolean gteq_b( LuaValue rhs )     { return rhs.isstring() ? rhs.strcmp(this)<=0 : super.gteq_b(rhs); }
	public boolean gteq_b( int rhs )       { typerror("attempt to compare string with number"); return false; }
	public boolean gteq_b( double rhs )    { typerror("attempt to compare string with number"); return false; }

	public LuaValue concat(LuaValue rhs)      { return rhs.concatTo(this); }
	public LuaValue concatTo(LuaNumber lhs)   { return concatTo(lhs.strvalue()); }
	public LuaValue concatTo(LuaString lhs)   {
		byte[] b = new byte[lhs.m_length+this.m_length];
		System.arraycopy(lhs.m_bytes, lhs.m_offset, b, 0, lhs.m_length);
		System.arraycopy(this.m_bytes, this.m_offset, b, lhs.m_length, this.m_length);
		return valueUsing(b, 0, b.length);
	}

	public int strcmp(LuaValue lhs)           { return -lhs.strcmp(this); }
	public int strcmp(LuaString rhs) {
		for ( int i=0, j=0; i<m_length && j<rhs.m_length; ++i, ++j ) {
			if ( m_bytes[m_offset+i] != rhs.m_bytes[rhs.m_offset+j] ) {
				return ((int)m_bytes[m_offset+i]) - ((int) rhs.m_bytes[rhs.m_offset+j]);
			}
		}
		return m_length - rhs.m_length;
	}

	private double checkarith() {
		double d = scannumber();
		if ( Double.isNaN(d) )
			aritherror();
		return d;
	}

	public int checkint() {
		return (int) (long) checkdouble();
	}
	public LuaInteger checkinteger() {
		return valueOf(checkint());
	}
	public long checklong() {
		return (long) checkdouble();
	}
	public double checkdouble() {
		double d = scannumber();
		if ( Double.isNaN(d) )
			argerror("number");
		return d;
	}
	public LuaNumber checknumber() {
		return valueOf(checkdouble());
	}
	public LuaNumber checknumber(String msg) {
		double d = scannumber();
		if ( Double.isNaN(d) )
			error(msg);
		return valueOf(d);
	}

	public boolean isnumber() {
		double d = scannumber();
		return ! Double.isNaN(d);
	}

	public boolean isint() {
		double d = scannumber();
		if ( Double.isNaN(d) )
			return false;
		int i = (int) d;
		return i == d;
	}

	public boolean islong() {
		double d = scannumber();
		if ( Double.isNaN(d) )
			return false;
		long l = (long) d;
		return l == d;
	}

	public byte    tobyte()        { return (byte) toint(); }
	public char    tochar()        { return (char) toint(); }
	public double  todouble()      { double d=scannumber(); return Double.isNaN(d)? 0: d; }
	public float   tofloat()       { return (float) todouble(); }
	public int     toint()         { return (int) tolong(); }
	public long    tolong()        { return (long) todouble(); }
	public short   toshort()       { return (short) toint(); }

	public double optdouble(double defval) {
		return checkdouble();
	}

	public int optint(int defval) {
		return checkint();
	}

	public LuaInteger optinteger(LuaInteger defval) {
		return checkinteger();
	}

	public long optlong(long defval) {
		return checklong();
	}

	public LuaNumber optnumber(LuaNumber defval) {
		return checknumber();
	}

	public LuaString optstring(LuaString defval) {
		return this;
	}

	public LuaValue tostring() {
		return this;
	}

	public String optjstring(String defval) {
		return tojstring();
	}

	public LuaString strvalue() {
		return this;
	}

	public LuaString substring( int beginIndex, int endIndex ) {
		final int off = m_offset + beginIndex;
		final int len = endIndex - beginIndex;
		return len >= m_length / 2?
			valueUsing(m_bytes, off, len):
			valueOf(m_bytes, off, len);
	}

	public int hashCode() {
		return m_hashcode;
	}

	public static int hashCode(byte[] bytes, int offset, int length) {
		int h = length;
		int step = (length>>5)+1;
		for (int l1=length; l1>=step; l1-=step)
		    h = h ^ ((h<<5)+(h>>2)+(((int) bytes[offset+l1-1] ) & 0x0FF ));
		return h;
	}

	public boolean equals( Object o ) {
		if ( o instanceof LuaString ) {
			return raweq( (LuaString) o );
		}
		return false;
	}

	public LuaValue eq( LuaValue val )    { return val.raweq(this)? TRUE: FALSE; }
	public boolean eq_b( LuaValue val )   { return val.raweq(this); }

	public boolean raweq( LuaValue val ) {
		return val.raweq(this);
	}

	public boolean raweq( LuaString s ) {
		if ( this == s )
			return true;
		if ( s.m_length != m_length )
			return false;
		if ( s.m_bytes == m_bytes && s.m_offset == m_offset )
			return true;
		if ( s.hashCode() != hashCode() )
			return false;
		for ( int i=0; i<m_length; i++ )
			if ( s.m_bytes[s.m_offset+i] != m_bytes[m_offset+i] )
				return false;
		return true;
	}

	public static boolean equals( LuaString a, int i, LuaString b, int j, int n ) {
		return equals( a.m_bytes, a.m_offset + i, b.m_bytes, b.m_offset + j, n );
	}

	private boolean byteseq(byte[] bytes, int off, int len) {
		return (m_length == len && equals(m_bytes, m_offset, bytes, off, len));
	}

	public static boolean equals( byte[] a, int i, byte[] b, int j, int n ) {
		if ( a.length < i + n || b.length < j + n )
			return false;
		while ( --n>=0 )
			if ( a[i++]!=b[j++] )
				return false;
		return true;
	}

	public void write(DataOutputStream writer, int i, int len) throws IOException {
		writer.write(m_bytes,m_offset+i,len);
	}

	public LuaValue len() {
		return LuaInteger.valueOf(m_length);
	}

	public int length() {
		return m_length;
	}

	public int rawlen() {
		return m_length;
	}

	public int luaByte(int index) {
		return m_bytes[m_offset + index] & 0x0FF;
	}

	public int charAt( int index ) {
		if ( index < 0 || index >= m_length )
			throw new IndexOutOfBoundsException();
		return luaByte( index );
	}

	public String checkjstring() {
		return tojstring();
	}

	public LuaString checkstring() {
		return this;
	}

	public InputStream toInputStream() {
		return new ByteArrayInputStream(m_bytes, m_offset, m_length);
	}

	public void copyInto( int strOffset, byte[] bytes, int arrayOffset, int len ) {
		System.arraycopy( m_bytes, m_offset+strOffset, bytes, arrayOffset, len );
	}

	public int indexOfAny( LuaString accept ) {
		final int ilimit = m_offset + m_length;
		final int jlimit = accept.m_offset + accept.m_length;
		for ( int i = m_offset; i < ilimit; ++i ) {
			for ( int j = accept.m_offset; j < jlimit; ++j ) {
				if ( m_bytes[i] == accept.m_bytes[j] ) {
					return i - m_offset;
				}
			}
		}
		return -1;
	}

	public int indexOf( byte b, int start ) {
		for ( int i=start; i < m_length; ++i ) {
			if ( m_bytes[m_offset+i] == b )
				return i;
		}
		return -1;
	}

	public int indexOf( LuaString s, int start ) {
		final int slen = s.length();
		final int limit =  m_length - slen;
		for ( int i=start; i <= limit; ++i ) {
			if ( equals( m_bytes, m_offset+i, s.m_bytes, s.m_offset, slen ) )
				return i;
		}
		return -1;
	}

	public int lastIndexOf( LuaString s ) {
		final int slen = s.length();
		final int limit =  m_length - slen;
		for ( int i=limit; i >= 0; --i ) {
			if ( equals( m_bytes, m_offset+i, s.m_bytes, s.m_offset, slen ) )
				return i;
		}
		return -1;
	}


	public static String decodeAsUtf8(byte[] bytes, int offset, int length) {
		int i,j,n,b;
		for ( i=offset,j=offset+length,n=0; i<j; ++n ) {
			switch ( 0xE0 & bytes[i++] ) {
			case 0xE0: ++i;
			case 0xC0: ++i;
			}
		}
		char[] chars=new char[n];
		for ( i=offset,j=offset+length,n=0; i<j; ) {
			chars[n++] = (char) (
				((b=bytes[i++])>=0||i>=j)? b:
				(b<-32||i+1>=j)? (((b&0x3f) << 6) | (bytes[i++]&0x3f)):
					(((b&0xf) << 12) | ((bytes[i++]&0x3f)<<6) | (bytes[i++]&0x3f)));
		}
		return new String(chars);
	}
	public static char[] toCharAsUtf8(byte[] bytes, int offset, int length) {
		int i,j,n,b;
		for ( i=offset,j=offset+length,n=0; i<j; ++n ) {
			switch ( 0xE0 & bytes[i++] ) {
				case 0xE0: ++i;
				case 0xC0: ++i;
			}
		}
		char[] chars=new char[n];
		for ( i=offset,j=offset+length,n=0; i<j; ) {
			chars[n++] = (char) (
					((b=bytes[i++])>=0||i>=j)? b:
							(b<-32||i+1>=j)? (((b&0x3f) << 6) | (bytes[i++]&0x3f)):
									(((b&0xf) << 12) | ((bytes[i++]&0x3f)<<6) | (bytes[i++]&0x3f)));
		}
		return chars;
	}
	public static int lengthAsUtf8(char[] chars) {
		int i,b;
		char c;
		for ( i=b=chars.length; --i>=0; )
			if ( (c=chars[i]) >=0x80 )
				b += (c>=0x800)? 2: 1;
		return b;
	}

	public static int encodeToUtf8(char[] chars, int nchars, byte[] bytes, int off) {
		char c;
		int j = off;
		for ( int i=0; i<nchars; i++ ) {
			if ( (c = chars[i]) < 0x80 ) {
				bytes[j++] = (byte) c;
			} else if ( c < 0x800 ) {
				bytes[j++] = (byte) (0xC0 | ((c>>6)  & 0x1f));
				bytes[j++] = (byte) (0x80 | ( c      & 0x3f));
			} else {
				bytes[j++] = (byte) (0xE0 | ((c>>12) & 0x0f));
				bytes[j++] = (byte) (0x80 | ((c>>6)  & 0x3f));
				bytes[j++] = (byte) (0x80 | ( c      & 0x3f));
			}
		}
		return j - off;
	}

	public boolean isValidUtf8() {
		for (int i=m_offset,j=m_offset+m_length; i<j;) {
			int c = m_bytes[i++];
			if ( c >= 0 ) continue;
			if ( ((c & 0xE0) == 0xC0)
					&& i<j
					&& (m_bytes[i++] & 0xC0) == 0x80) continue;
			if ( ((c & 0xF0) == 0xE0)
					&& i+1<j
					&& (m_bytes[i++] & 0xC0) == 0x80
					&& (m_bytes[i++] & 0xC0) == 0x80) continue;
			return false;
		}
		return true;
	}


	public LuaValue tonumber() {
		double d = scannumber();
		return Double.isNaN(d)? NIL: valueOf(d);
	}

	public LuaValue tonumber( int base ) {
		double d = scannumber( base );
		return Double.isNaN(d)? NIL: valueOf(d);
	}

	public double scannumber() {
		int i=m_offset,j=m_offset+m_length;
		while ( i<j && m_bytes[i]==' ' ) ++i;
		while ( i<j && m_bytes[j-1]==' ' ) --j;
		if ( i>=j )
			return Double.NaN;
		if ( m_bytes[i]=='0' && i+1<j && (m_bytes[i+1]=='x'||m_bytes[i+1]=='X'))
			return scanlong(16, i+2, j);
		double l = scanlong(10, i, j);
		return Double.isNaN(l)? scandouble(i,j): l;
	}

	public double scannumber(int base) {
		if ( base < 2 || base > 36 )
			return Double.NaN;
		int i=m_offset,j=m_offset+m_length;
		while ( i<j && m_bytes[i]==' ' ) ++i;
		while ( i<j && m_bytes[j-1]==' ' ) --j;
		if ( i>=j )
			return Double.NaN;
		return scanlong( base, i, j );
	}

	private double scanlong( int base, int start, int end ) {
		long x = 0;
		boolean neg = (m_bytes[start] == '-');
		for ( int i=(neg?start+1:start); i<end; i++ ) {
			int digit = m_bytes[i] - (base<=10||(m_bytes[i]>='0'&&m_bytes[i]<='9')? '0':
					m_bytes[i]>='A'&&m_bytes[i]<='Z'? ('A'-10): ('a'-10));
			if ( digit < 0 || digit >= base )
				return Double.NaN;
			x = x * base + digit;
			if ( x < 0 )
				return Double.NaN;
		}
		return neg? -x: x;
	}

	private double scandouble(int start, int end) {
		if ( end>start+64 ) end=start+64;
		for ( int i=start; i<end; i++ ) {
			switch ( m_bytes[i] ) {
			case '-':
			case '+':
			case '.':
			case 'e': case 'E':
			case '0': case '1': case '2': case '3': case '4':
			case '5': case '6': case '7': case '8': case '9':
				break;
			default:
				return Double.NaN;
			}
		}
		char [] c = new char[end-start];
		for ( int i=start; i<end; i++ )
			c[i-start] = (char) m_bytes[i];
		try {
			return Double.parseDouble(new String(c));
		} catch ( Exception e ) {
			return Double.NaN;
		}
	}

	public void printToStream(PrintStream ps) {
		for (int i = 0, n = m_length; i < n; i++) {
			int c = m_bytes[m_offset+i];
			ps.print((char) c);
		}
	}
}
