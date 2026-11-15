package org.luaj.vm2;


public class LuaInteger extends LuaNumber {

	private static final LuaInteger[] intValues = new LuaInteger[512];
	static {
		for ( int i=0; i<512; i++ )
			intValues[i] = new LuaInteger(i-256);
	}

	public static LuaInteger valueOf(int i) {
		return i<=255 && i>=-256? intValues[i+256]: new LuaInteger(i);
	};

	public static LuaNumber valueOf(long i) {
		return (i<Long.MAX_VALUE||i>Long.MIN_VALUE)? (i<=255 && i>=-256? intValues[(int) (i+256)]:
			(LuaNumber) new LuaInteger(i)):
			(LuaNumber) LuaDouble.valueOf(i);
	}

	public final long v;

	LuaInteger(long i) {
		this.v = i;
	}

	public boolean isint() {		return true;	}
	public boolean isinttype() {	return true;	}
	public boolean islong() {		return true;	}

	public byte    tobyte()        { return (byte) v; }
	public char    tochar()        { return (char) v; }
	public double  todouble()      { return v; }
	public float   tofloat()       { return v; }
	public int     toint()         { return (int) v; }
	public long    tolong()        { return v; }
	public short   toshort()       { return (short) v; }

	public double      optdouble(double defval)            { return v; }
	public int         optint(int defval)                  { return (int) v;  }
	public LuaInteger  optinteger(LuaInteger defval)       { return this; }
	public long        optlong(long defval)                { return v; }

	public String tojstring() {
		return Long.toString(v);
	}

	public LuaString strvalue() {
		return LuaString.valueOf(Long.toString(v));
	}

	public LuaString optstring(LuaString defval) {
		return LuaString.valueOf(Long.toString(v));
	}

	public LuaValue tostring() {
		return LuaString.valueOf(Long.toString(v));
	}

	public String optjstring(String defval) {
		return Long.toString(v);
	}

	public LuaInteger checkinteger() {
		return this;
	}

	public boolean isstring() {
		return true;
	}

	public int hashCode() {
		return Long.valueOf(v).hashCode();
	}

	public static int hashCode(int x) {
		return x;
	}

	public LuaValue neg() { return valueOf(-(long)v); }

	public boolean equals(Object o) { return o instanceof LuaInteger? ((LuaInteger)o).v == v: false; }

	public LuaValue eq( LuaValue val )    { return val.raweq(v)? TRUE: FALSE; }
	public boolean eq_b( LuaValue val )   { return val.raweq(v); }

	public boolean raweq( LuaValue val )  { return val.raweq(v); }
	public boolean raweq( double val )    { return v == val; }
	public boolean raweq( long val )       { return v == val; }

	public LuaValue   add( LuaValue rhs )        { return rhs.add(v); }
	public LuaValue   add( double lhs )     { return LuaDouble.valueOf(lhs + v); }
	public LuaValue   add( long lhs )        { return LuaInteger.valueOf(lhs + (long)v); }
	public LuaValue   sub( LuaValue rhs )        { return rhs.subFrom(v); }
	public LuaValue   sub( double rhs )        { return LuaDouble.valueOf(v - rhs); }
	public LuaValue   sub( long rhs )        { return LuaDouble.valueOf(v - rhs); }
	public LuaValue   subFrom( double lhs )   { return LuaDouble.valueOf(lhs - v); }
	public LuaValue   subFrom( long lhs )      { return LuaInteger.valueOf(lhs - (long)v); }
	public LuaValue   mul( LuaValue rhs )        { return rhs.mul(v); }
	public LuaValue   mul( double lhs )   { return LuaDouble.valueOf(lhs * v); }
	public LuaValue   mul( long lhs )      { return LuaInteger.valueOf(lhs * (long)v); }
	public LuaValue   pow( LuaValue rhs )        { return rhs.powWith(v); }
	public LuaValue   pow( double rhs )        { return LuaNumber.valueOf(Math.pow(v,rhs)); }
	public LuaValue   pow( long rhs )        { return LuaNumber.valueOf(Math.pow(v,rhs)); }
	public LuaValue   powWith( double lhs )   { return LuaNumber.valueOf(Math.pow(lhs,v)); }
	public LuaValue   powWith( long lhs )      { return LuaNumber.valueOf(Math.pow(lhs,v)); }
	public LuaValue   div( LuaValue rhs )        { return rhs.divInto(v); }
	public LuaValue   div( double rhs )        { return LuaDouble.ddiv(v,rhs); }
	public LuaValue   div( long rhs )        { return LuaDouble.ddiv(v,rhs); }
	public LuaValue   divInto( double lhs )   { return LuaDouble.ddiv(lhs,v); }
	public LuaValue   mod( LuaValue rhs )        { return rhs.modFrom(v); }
	public LuaValue   mod( double rhs )        { return LuaDouble.dmod(v,rhs); }
	public LuaValue   mod( long rhs )        { return LuaDouble.dmod(v,rhs); }
	public LuaValue   modFrom( double lhs )   { return LuaDouble.dmod(lhs,v); }

	public LuaValue   idiv( LuaValue rhs )        { return rhs.idiv(v); }
	public LuaValue   idiv( long lhs )     { return LuaInteger.valueOf(lhs / v); }

	public LuaValue   band( LuaValue rhs )        { return rhs.band(v); }
	public LuaValue   band( long lhs )     { return LuaInteger.valueOf(lhs & v); }

	public LuaValue   bor( LuaValue rhs )        { return rhs.bor(v); }
	public LuaValue   bor( long lhs )     { return LuaInteger.valueOf(lhs | v); }

	public LuaValue   bxor( LuaValue rhs )        { return rhs.bxor(v); }
	public LuaValue   bxor( long lhs )     { return LuaInteger.valueOf(lhs ^ v); }

	public LuaValue   shl( LuaValue rhs )        { return rhs.shl(v); }
	public LuaValue   shl( long lhs )     { return LuaInteger.valueOf(lhs << v); }

	public LuaValue   shr( LuaValue rhs )        { return rhs.shr(v); }
	public LuaValue   shr( long lhs )     { return LuaInteger.valueOf(lhs >> v); }

	public LuaValue   bnot()     { return LuaInteger.valueOf( ~v); }

	public LuaValue   lt( LuaValue rhs )         { return rhs instanceof LuaNumber ? (rhs.gt_b(v)? TRUE: FALSE) : super.lt(rhs); }
	public LuaValue   lt( double rhs )      { return v < rhs? TRUE: FALSE; }
	public LuaValue   lt( long rhs )         { return v < rhs? TRUE: FALSE; }
	public boolean lt_b( LuaValue rhs )       { return rhs instanceof LuaNumber ? rhs.gt_b(v) : super.lt_b(rhs); }
	public boolean lt_b( long rhs )         { return v < rhs; }
	public boolean lt_b( double rhs )      { return v < rhs; }
	public LuaValue   lteq( LuaValue rhs )       { return rhs instanceof LuaNumber ? (rhs.gteq_b(v)? TRUE: FALSE) : super.lteq(rhs); }
	public LuaValue   lteq( double rhs )    { return v <= rhs? TRUE: FALSE; }
	public LuaValue   lteq( long rhs )       { return v <= rhs? TRUE: FALSE; }
	public boolean lteq_b( LuaValue rhs )     { return rhs instanceof LuaNumber ? rhs.gteq_b(v) : super.lteq_b(rhs); }
	public boolean lteq_b( long rhs )       { return v <= rhs; }
	public boolean lteq_b( double rhs )    { return v <= rhs; }
	public LuaValue   gt( LuaValue rhs )         { return rhs instanceof LuaNumber ? (rhs.lt_b(v)? TRUE: FALSE) : super.gt(rhs); }
	public LuaValue   gt( double rhs )      { return v > rhs? TRUE: FALSE; }
	public LuaValue   gt( long rhs )         { return v > rhs? TRUE: FALSE; }
	public boolean gt_b( LuaValue rhs )       { return rhs instanceof LuaNumber ? rhs.lt_b(v) : super.gt_b(rhs); }
	public boolean gt_b( long rhs )         { return v > rhs; }
	public boolean gt_b( double rhs )      { return v > rhs; }
	public LuaValue   gteq( LuaValue rhs )       { return rhs instanceof LuaNumber ? (rhs.lteq_b(v)? TRUE: FALSE) : super.gteq(rhs); }
	public LuaValue   gteq( double rhs )    { return v >= rhs? TRUE: FALSE; }
	public LuaValue   gteq( long rhs )       { return v >= rhs? TRUE: FALSE; }
	public boolean gteq_b( LuaValue rhs )     { return rhs instanceof LuaNumber ? rhs.lteq_b(v) : super.gteq_b(rhs); }
	public boolean gteq_b( long rhs )       { return v >= rhs; }
	public boolean gteq_b( double rhs )    { return v >= rhs; }

	public int strcmp( LuaString rhs )      { typerror("attempt to compare number with string"); return 0; }

	public int checkint() {
		return (int) v;
	}
	public long checklong() {
		return v;
	}
	public double checkdouble() {
		return v;
	}
	public String checkjstring() {
		return String.valueOf(v);
	}
	public LuaString checkstring() {
		return valueOf( String.valueOf(v) );
	}

}
