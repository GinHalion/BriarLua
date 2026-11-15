package org.luaj.vm2;


public class Lua {
	public static final String _VERSION = "Luaj-jse 3.0.1";

	public static final int LUA_MULTRET = -1;

	public static final boolean LUA_FUNC_ENV = false;

	public static final boolean LUA_LOCAL_ENV = true;

	public static final boolean LUA_JAVA_OO = true;




	public static final int	iABC = 0;
	public static final int	iABx = 1;
	public static final int	iAsBx = 2;
	public static final int	iAx = 3;


	public static final int SIZE_C		= 9;
	public static final int SIZE_B		= 9;
	public static final int SIZE_Bx		= (SIZE_C + SIZE_B);
	public static final int SIZE_A		= 8;
	public static final int SIZE_Ax		= (SIZE_C + SIZE_B + SIZE_A);

	public static final int SIZE_OP		= 6;

	public static final int POS_OP		= 0;
	public static final int POS_A		= (POS_OP + SIZE_OP);
	public static final int POS_C		= (POS_A + SIZE_A);
	public static final int POS_B		= (POS_C + SIZE_C);
	public static final int POS_Bx		= POS_C;
	public static final int POS_Ax		= POS_A;


	public static final int MAX_OP          = ((1<<SIZE_OP)-1);
	public static final int MAXARG_A        = ((1<<SIZE_A)-1);
	public static final int MAXARG_B        = ((1<<SIZE_B)-1);
	public static final int MAXARG_C        = ((1<<SIZE_C)-1);
	public static final int MAXARG_Bx       = ((1<<SIZE_Bx)-1);
	public static final int MAXARG_sBx      = (MAXARG_Bx>>1);
	public static final int MAXARG_Ax       = ((1<<SIZE_Ax)-1);

	public static final int MASK_OP = ((1<<SIZE_OP)-1)<<POS_OP;
	public static final int MASK_A  = ((1<<SIZE_A)-1)<<POS_A;
	public static final int MASK_B  = ((1<<SIZE_B)-1)<<POS_B;
	public static final int MASK_C  = ((1<<SIZE_C)-1)<<POS_C;
	public static final int MASK_Bx = ((1<<SIZE_Bx)-1)<<POS_Bx;
	public static final int MASK_Ax = ((1<<SIZE_Ax)-1)<<POS_Ax;

	public static final int MASK_NOT_OP = ~MASK_OP;
	public static final int MASK_NOT_A  = ~MASK_A;
	public static final int MASK_NOT_B  = ~MASK_B;
	public static final int MASK_NOT_C  = ~MASK_C;
	public static final int MASK_NOT_Bx = ~MASK_Bx;

	public static int GET_OPCODE(int i) {
		return (i >> POS_OP) & MAX_OP;
	}

	public static int GETARG_A(int i) {
		return (i >> POS_A) & MAXARG_A;
	}

	public static int GETARG_Ax(int i) {
		return (i >> POS_Ax) & MAXARG_Ax;
	}

	public static int GETARG_B(int i) {
		return (i >> POS_B) & MAXARG_B;
	}

	public static int GETARG_C(int i) {
		return (i >> POS_C) & MAXARG_C;
	}

	public static int GETARG_Bx(int i) {
		return (i >> POS_Bx) & MAXARG_Bx;
	}

	public static int GETARG_sBx(int i) {
		return ((i >> POS_Bx) & MAXARG_Bx) - MAXARG_sBx;
	}



	public static final int BITRK		= (1 << (SIZE_B - 1));

	public static boolean ISK(int x) {
		return 0 != ((x) & BITRK);
	}

	public static int INDEXK(int r) {
		return ((int)(r) & ~BITRK);
	}

	public static final int MAXINDEXRK	= (BITRK - 1);

	public static int RKASK(int x) {
		return ((x) | BITRK);
	}


	public static final int  NO_REG		= MAXARG_A;





	public static final int OP_MOVE = 0;
	public static final int OP_LOADK = 1;
	public static final int OP_LOADKX = 2;
	public static final int OP_LOADBOOL = 3;
	public static final int OP_LOADNIL = 4;
	public static final int OP_GETUPVAL = 5;

	public static final int OP_GETTABUP = 6;
	public static final int OP_GETTABLE = 7;

	public static final int OP_SETTABUP = 8;
	public static final int OP_SETUPVAL = 9;
	public static final int OP_SETTABLE = 10;

	public static final int OP_NEWTABLE = 11;

	public static final int OP_SELF = 12;

	public static final int OP_ADD = 13;
	public static final int OP_SUB = 14;
	public static final int OP_MUL = 15;
	public static final int OP_DIV = 16;
	public static final int OP_MOD = 17;
	public static final int OP_POW = 18;
	public static final int OP_UNM = 19;
	public static final int OP_NOT = 20;
	public static final int OP_LEN = 21;

	public static final int OP_CONCAT = 22;

	public static final int OP_JMP = 23;
	public static final int OP_EQ = 24;
	public static final int OP_LT = 25;
	public static final int OP_LE = 26;

	public static final int OP_TEST = 27;
	public static final int OP_TESTSET = 28;

	public static final int OP_CALL = 29;
	public static final int OP_TAILCALL = 30;
	public static final int OP_RETURN = 31;

	public static final int OP_FORLOOP = 32;
	public static final int OP_FORPREP = 33;

	public static final int OP_TFORCALL = 34;
	public static final int OP_TFORLOOP = 35;
	public static final int OP_SETLIST = 36;

	public static final int OP_CLOSURE = 37;

	public static final int OP_VARARG = 38;

	public static final int OP_IDIV = 39;
	public static final int OP_BAND = 40;
	public static final int OP_BOR = 41;
	public static final int OP_BXOR = 42;
	public static final int OP_SHL = 43;
	public static final int OP_SHR = 44;
	public static final int OP_BNOT = 45;
	public static final int OP_GETGLOBAL = 46;
	public static final int OP_SETGLOBAL = 47;
	public static final int OP_NEWLIST = 48;
	public static final int OP_GETENV = 49;
	public static final int OP_SETENV = 50;
    public static final int OP_DEFER = 51;
	public static final int OP_TFOREACH = 52;
	public static final int OP_TCALL = 53;
	public static final int OP_IMPORT = 54;
	public static final int OP_MODULE = 55;
	public static final int OP_EXTRAARG = 56;


	public static final int NUM_OPCODES	= OP_EXTRAARG + 1;

	public static final int OP_GT  = 63;
	public static final int OP_GE  = 62;
	public static final int OP_NEQ = 61;
	public static final int OP_AND = 60;
	public static final int OP_OR  = 59;




	  public static final int OpArgN = 0;
	  public static final int OpArgU = 1;
	  public static final int OpArgR = 2;
	  public static final int OpArgK = 3;

	  public static final int[] luaP_opmodes = {
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),
		 (0<<7) | (1<<6) | (OpArgN<<4) | (OpArgN<<2) | (iABx),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (0<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (0<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (0<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgR<<2) | (iABC),
		 (0<<7) | (0<<6) | (OpArgR<<4) | (OpArgN<<2) | (iAsBx),
		 (1<<7) | (0<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (1<<7) | (0<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (1<<7) | (0<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
		 (1<<7) | (0<<6) | (OpArgN<<4) | (OpArgU<<2) | (iABC),
		 (1<<7) | (1<<6) | (OpArgR<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (0<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iAsBx),
		 (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iAsBx),
		 (0<<7) | (0<<6) | (OpArgN<<4) | (OpArgU<<2) | (iABC),
		 (1<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iAsBx),
		 (0<<7) | (0<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABx),
		 (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgK<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgR<<4) | (OpArgN<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),
			  (0<<7) | (0<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),

			  (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABx),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),
			  (0<<7) | (0<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),
			  (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgN<<2) | (iABC),
			  (0<<7) | (0<<6) | (OpArgN<<4) | (OpArgU<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgU<<4) | (OpArgU<<2) | (iABC),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),
			  (0<<7) | (1<<6) | (OpArgK<<4) | (OpArgN<<2) | (iABx),

			  (0<<7) | (0<<6) | (OpArgU<<4) | (OpArgU<<2) | (iAx),
	  };

	public static int getOpMode(int m) {
		return luaP_opmodes[m] & 3;
	}
	public static int getBMode(int m) {
		return (luaP_opmodes[m] >> 4) & 3;
	}
	public static int getCMode(int m) {
		return (luaP_opmodes[m] >> 2) & 3;
	}
	public static boolean testAMode(int m) {
		return 0 != (luaP_opmodes[m] & (1 << 6));
	}
	public static boolean testTMode(int m) {
		return 0 != (luaP_opmodes[m] & (1 << 7));
	}

	public static final int LFIELDS_PER_FLUSH = 50;

	private static final int MAXSRC = 80;

	public static String chunkid( String source ) {
		 if ( source.startsWith("=") )
			 return source.substring(1);
		 String end = "";
		 if ( source.startsWith("@") ) {
			 source = source.substring(1);
		 } else {
			 source = "[string \""+source;
			 end = "\"]";
		 }
		 int n = source.length() + end.length();
		 if ( n > MAXSRC )
			 source = source.substring(0,MAXSRC-end.length()-3) + "...";
		 return source + end;
	}
}
