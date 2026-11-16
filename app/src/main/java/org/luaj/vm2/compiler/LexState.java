package org.luaj.vm2.compiler;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.util.Log;

import com.myopicmobile.textwarrior.common.Flag;
import com.myopicmobile.textwarrior.common.Pair;

import org.luaj.vm2.LocVars;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.VarType;
import org.luaj.vm2.compiler.FuncState.BlockCnt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;


public class LexState extends Constants {

    protected static final String RESERVED_LOCAL_VAR_FOR_CONTROL = "(for control)";
    protected static final String RESERVED_LOCAL_VAR_FOR_STATE = "(for state)";
    protected static final String RESERVED_LOCAL_VAR_FOR_GENERATOR = "(for generator)";
    protected static final String RESERVED_LOCAL_VAR_FOR_STEP = "(for step)";
    protected static final String RESERVED_LOCAL_VAR_FOR_LIMIT = "(for limit)";
    protected static final String RESERVED_LOCAL_VAR_FOR_INDEX = "(for index)";

    protected static final String[] RESERVED_LOCAL_VAR_KEYWORDS = new String[]{
            RESERVED_LOCAL_VAR_FOR_CONTROL,
            RESERVED_LOCAL_VAR_FOR_GENERATOR,
            RESERVED_LOCAL_VAR_FOR_INDEX,
            RESERVED_LOCAL_VAR_FOR_LIMIT,
            RESERVED_LOCAL_VAR_FOR_STATE,
            RESERVED_LOCAL_VAR_FOR_STEP
    };
    private static final Hashtable RESERVED_LOCAL_VAR_KEYWORDS_TABLE = new Hashtable();

    static {
        for (int i = 0; i < RESERVED_LOCAL_VAR_KEYWORDS.length; i++)
            RESERVED_LOCAL_VAR_KEYWORDS_TABLE.put(RESERVED_LOCAL_VAR_KEYWORDS[i], Boolean.TRUE);
    }

    private static final int EOZ = (-1);
    private static final int MAX_INT = Integer.MAX_VALUE - 2;
    private static final int UCHAR_MAX = 255;
    private static final int LUAI_MAXCCALLS = 200;
    private  Flag abort=new Flag();
    public int lastidx;
    public int lasttoken;
    private boolean CURLY;
    private int lastNameIdx;

    private static final String LUA_QS(String s) {
        return "'" + s + "'";
    }

    private static final String LUA_QL(Object o) {
        return LUA_QS(String.valueOf(o));
    }

    private static final int LUA_COMPAT_LSTR = 0;
    private static final boolean LUA_COMPAT_VARARG = true;

    public static boolean isReservedKeyword(String varName) {
        return RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName);
    }

    static final int NO_JUMP = (-1);

    static final int
            OPR_ADD = 0, OPR_SUB = 1, OPR_MUL = 2, OPR_MOD = 3, OPR_POW = 4,
            OPR_DIV = 5,
            OPR_IDIV = 6,
            OPR_BAND = 7, OPR_BOR = 8, OPR_BXOR = 9,
            OPR_SHL = 10, OPR_SHR = 11,
            OPR_CONCAT = 12,
            OPR_EQ = 13, OPR_LT = 14, OPR_LE = 15,
            OPR_NE = 16, OPR_GT = 17, OPR_GE = 18,
            OPR_AND = 19, OPR_OR = 20,
            OPR_NOBINOPR = 21;

    static final int
            OPR_MINUS = 0, OPR_NOT = 1, OPR_LEN = 2, OPR_NOUNOPR = 3, OPR_BNOT = 4;

    public static final int
            VVOID = 0,
            VNIL = 1,
            VTRUE = 2,
            VFALSE = 3,
            VK = 4,
            VKNUM = 5,
            VNONRELOC = 6,
            VLOCAL = 7,
            VGLOBAL = 8,
            VENV = 9,
            VUPVAL = 10,
            VINDEXED = 11,
            VJMP = 12,
            VRELOCABLE = 13,
            VCALL = 14,
            VVARARG = 15;

    private static class SemInfo {
        LuaValue r;
        LuaString ts;
    }

    ;

    private static class Token {
        int token;
        final SemInfo seminfo = new SemInfo();

        public void set(Token other) {
            this.token = other.token;
            this.seminfo.r = other.seminfo.r;
            this.seminfo.ts = other.seminfo.ts;
        }
    }

    ;

    int currentidx;
    int current;
    int linenumber;
    int lastline;
    final Token t = new Token();
    final Token lookahead = new Token();
    FuncState fs;
    LuaC.CompileState L;
    InputStream z;
    char[] buff;
    int nbuff;
    Dyndata dyd = new Dyndata();
    LuaString source;
    LuaString envn;
    byte decpoint;

    final static String luaX_tokens[] = {
            "and", "break", "case", "catch", "continue", "default", "defer", "do", "else", "elseif",
            "end", "false", "finally", "for", "function", "goto", "if", "import",
            "in", "lambda", "local", "module", "nil", "not", "or", "repeat",
            "return", "switch", "then", "true", "try", "until", "when", "while",
            "..", "...", "==", ">=", "<=", "~=", "//", "<<", ">>",
            "::", "<eos>", "<number>", "<name>", "<string>", "<eof>",
    };

    final static String luaX_cn_tokens[] = {
            "与", "跳出", "case", "捕获", "跳过", "默认", "延时", "执行", "否则", "否则如果",
            "结束", "假", "finally", "循环", "函数", "跳转", "如果", "导入",
            "在", "lambda", "局部", "模块", "无", "非", "或", "重复",
            "返回", "switch", "那么", "真", "尝试", "直到", "when", "当",
    };

    final static int TK_AND = 257;
    final static int TK_BREAK = TK_AND + 1;
    final static int TK_CASE = TK_BREAK + 1;
    final static int TK_CATCH = TK_CASE + 1;
    final static int TK_CONTINUE = TK_CATCH + 1;
    final static int TK_DEFAULT = TK_CONTINUE + 1;
    final static int TK_DEFER = TK_DEFAULT + 1;
    final static int TK_DO = TK_DEFER + 1;
    final static int TK_ELSE = TK_DO + 1;
    final static int TK_ELSEIF = TK_ELSE + 1;
    final static int TK_END = TK_ELSEIF + 1;
    final static int TK_FALSE = TK_END + 1;
    final static int TK_FINALLY = TK_FALSE + 1;
    final static int TK_FOR = TK_FINALLY + 1;
    final static int TK_FUNCTION = TK_FOR + 1;
    final static int TK_GOTO = TK_FUNCTION + 1;
    final static int TK_IF = TK_GOTO + 1;
    final static int TK_IMPORT = TK_IF + 1;
    final static int TK_IN = TK_IMPORT + 1;
    final static int TK_LAMBDA = TK_IN + 1;
    final static int TK_LOCAL = TK_LAMBDA + 1;
    final static int TK_MODULE = TK_LOCAL + 1;
    final static int TK_NIL = TK_MODULE + 1;
    final static int TK_NOT = TK_NIL + 1;
    final static int TK_OR = TK_NOT + 1;
    final static int TK_REPEAT = TK_OR + 1;
    final static int TK_RETURN = TK_REPEAT + 1;
    final static int TK_SWITCH = TK_RETURN + 1;
    final static int TK_THEN = TK_SWITCH + 1;
    final static int TK_TRUE = TK_THEN + 1;
    final static int TK_TRY = TK_TRUE + 1;
    final static int TK_UNTIL = TK_TRY + 1;
    final static int TK_WHEN = TK_UNTIL + 1;
    final static int TK_WHILE = TK_WHEN + 1;
    final static int TK_CONCAT = TK_WHILE + 1;
    final static int TK_DOTS = TK_CONCAT + 1;
    final static int TK_EQ = TK_DOTS + 1;
    final static int TK_GE = TK_EQ + 1;
    final static int TK_LE = TK_GE + 1;
    final static int TK_NE = TK_LE + 1;
    final static int TK_IDIV = TK_NE + 1;
    final static int TK_SHL = TK_IDIV + 1;
    final static int TK_SHR = TK_SHL + 1;
    final static int TK_DBCOLON = TK_SHR + 1;
    final static int TK_EOS = TK_DBCOLON + 1;
    final static int TK_NUMBER = TK_EOS + 1;
    final static int TK_NAME = TK_NUMBER + 1;
    final static int TK_STRING = TK_NAME + 1;

    final static int FIRST_RESERVED = TK_AND;
    final static int NUM_RESERVED = TK_WHILE + 1 - FIRST_RESERVED;

    final static Hashtable RESERVED = new Hashtable();

    static {
        for (int i = 0; i < NUM_RESERVED; i++) {
            LuaString ts = (LuaString) LuaValue.valueOf(luaX_tokens[i]);
            RESERVED.put(ts, new Integer(FIRST_RESERVED + i));
        }
    }

    private final static int[] luai_ctype_ = {
            0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x08, 0x08, 0x08, 0x08, 0x08, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0c, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16,
            0x16, 0x16, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x05,
            0x04, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x00,

            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,


            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
            0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,


            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    };
    private final static int ALPHABIT = 0, DIGITBIT = 1, PRINTBIT = 2, SPACEBIT = 3, XDIGITBIT = 4;

    private boolean testprop(int c, int p) {
        return (luai_ctype_[(c) + 1] & (p)) != 0;
    }

    private static int MASK(int B) {
        return (1 << (B));
    }

    private boolean isalnum(int c) {
        return testprop(c, (MASK(ALPHABIT) | MASK(DIGITBIT)));
    }

    private boolean isalpha(int c) {
        return testprop(c, MASK(ALPHABIT));
    }

    private boolean isdigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private boolean isxdigit(int c) {
        return testprop(c, MASK(XDIGITBIT));
    }

    private boolean isspace(int c) {
        return testprop(c, MASK(SPACEBIT));
    }

    private boolean inLexer;

    public LexState(LuaC.CompileState state, InputStream stream, boolean bool, Flag _abort) {
        this.z = stream;
        this.buff = new char[32];
        this.L = state;
        this.inLexer = bool;
        this.abort=_abort;
    }

    public LexState(LuaC.CompileState state, InputStream stream) {
        this.z = stream;
        this.buff = new char[32];
        this.L = state;
    }

    void nextChar() {
        try {
            current = z.read();
            currentidx++;
        } catch (IOException e) {
            e.printStackTrace();
            current = EOZ;
        }
    }

    boolean currIsNewline() {
        return current == '\n' || current == '\r';
    }

    void save_and_next() {
        save(current);
        nextChar();
    }

    void save(int c) {
        if (buff == null || nbuff + 1 > buff.length)
            buff = realloc(buff, nbuff * 2 + 1);
        buff[nbuff++] = (char) c;
    }


    String token2str(int token) {
        if (token < FIRST_RESERVED) {
            return iscntrl(token) ?
                    L.pushfstring("char(" + ((int) token) + ")") :
                    L.pushfstring(String.valueOf((char) token));
        } else {
            return luaX_tokens[token - FIRST_RESERVED];
        }
    }

    private static boolean iscntrl(int token) {
        return token < ' ';
    }

    String txtToken(int token) {
        switch (token) {
            case TK_NAME:
            case TK_STRING:
            case TK_NUMBER:
                return LuaString.valueOf(buff, 0, nbuff).tojstring();
            default:
                return token2str(token);
        }
    }

    public static String errormsg;
    public static int errorline;
    public static int erroridx;

    void lexerror(String msg, int token) {

        if (inLexer) {
            if (erroridx < 0) {
                errormsg = linenumber + ": " + msg + " 靠近 " + txtToken(token);
                erroridx = lastidx;
            }
        }
        String cid = Lua.chunkid(source.tojstring());
        if (token != 0)
            msg = L.pushfstring(linenumber + ": 语法错误: " + msg + " 靠近 " + txtToken(token));
        else
            msg = L.pushfstring(cid + ":" + linenumber + ": " + msg);

        throw new LuaError(msg);
    }

    void syntaxerror(String msg) {
        lexerror(msg, t.token);
    }

    LuaString newstring(String s) {
        return L.newTString(s);
    }

    LuaString newstring(char[] chars, int offset, int len) {
        LuaString s = L.newTString(LuaString.valueOf(chars, offset, len));
        if (inLexer) {
            currentidx -= len;
            currentidx += s.lengthAsUtf8();
        }
        return s;
    }

    void inclinenumber() {
        int old = current;
        _assert(currIsNewline());
        nextChar();
        if (currIsNewline() && current != old)
            nextChar();
        if (++linenumber >= MAX_INT)
            syntaxerror("chunk has too many lines");
    }

    void setinput(LuaC.CompileState L, int firstByte, InputStream z, LuaString source) {
        this.decpoint = '.';
        this.L = L;
        this.lookahead.token = TK_EOS;
        this.z = z;
        this.fs = null;
        this.linenumber = 1;
        this.lastline = 1;
        this.source = source;
        this.envn = LuaValue.ENV;
        this.nbuff = 0;
        this.current = firstByte;
        this.skipShebang();
    }

    private void skipShebang() {
        if (current == '#')
            while (!currIsNewline() && current != EOZ)
                nextChar();
    }





    boolean check_next(String set) {
        if (set.indexOf(current) < 0)
            return false;
        save_and_next();
        return true;
    }

    void buffreplace(char from, char to) {
        int n = nbuff;
        char[] p = buff;
        while ((--n) >= 0)
            if (p[n] == from)
                p[n] = to;
    }

    LuaValue strx2number(String str, SemInfo seminfo) {
        char[] c = str.toCharArray();
        int s = 0;
        while (s < c.length && isspace(c[s]))
            ++s;
        double sgn = 1.0;
        if (s < c.length && c[s] == '-') {
            sgn = -1.0;
            ++s;
        }
        if (s + 2 >= c.length)
            return LuaValue.ZERO;
        if (c[s++] != '0')
            return LuaValue.ZERO;
        if (c[s] != 'x' && c[s] != 'X')
            return LuaValue.ZERO;
        ++s;

        double m = 0;
        int e = 0;
        while (s < c.length && isxdigit(c[s]))
            m = (m * 16) + hexvalue(c[s++]);
        if (s < c.length && c[s] == '.') {
            ++s;
            while (s < c.length && isxdigit(c[s])) {
                m = (m * 16) + hexvalue(c[s++]);
                e -= 4;
            }
        }
        if (s < c.length && (c[s] == 'p' || c[s] == 'P')) {
            ++s;
            int exp1 = 0;
            boolean neg1 = false;
            if (s < c.length && c[s] == '-') {
                neg1 = true;
                ++s;
            }
            while (s < c.length && isdigit(c[s]))
                exp1 = exp1 * 10 + c[s++] - '0';
            if (neg1)
                exp1 = -exp1;
            e += exp1;
        }
        return LuaValue.valueOf(sgn * m * Math.pow(2.0, e));
    }

    boolean str2d(String str, SemInfo seminfo) {
        if (str.indexOf('n') >= 0 || str.indexOf('N') >= 0)
            seminfo.r = LuaValue.ZERO;
        else if (str.indexOf('x') >= 0 || str.indexOf('X') >= 0)
            seminfo.r = strx2number(str, seminfo);
        else
            seminfo.r = LuaValue.valueOf(Double.parseDouble(str.trim()));
        return true;
    }

    void read_numeral(SemInfo seminfo) {
        String expo = "Ee";
        int first = current;
        _assert(isdigit(current));
        save_and_next();
        if (first == '0' && check_next("Xx"))
            expo = "Pp";
        while (true) {
            if (check_next(expo))
                check_next("+-");
            if (isxdigit(current) || current == '.')
                save_and_next();
            else
                break;
        }
        save('\0');
        String str = new String(buff, 0, nbuff);
        str2d(str, seminfo);
    }

    int skip_sep() {
        int count = 0;
        int s = current;
        _assert(s == '[' || s == ']');
        save_and_next();
        while (current == '=') {
            save_and_next();
            count++;
        }
        return (current == s) ? count : (-count) - 1;
    }

    void read_long_string(SemInfo seminfo, int sep) {
        int cont = 0;
        save_and_next();
        if (currIsNewline())
            inclinenumber();
        for (boolean endloop = false; !endloop; ) {
            switch (current) {
                case EOZ:
                    lexerror((seminfo != null) ? "未完成的长字符"
                            : "未完成的长注释", TK_EOS);
                    break;
                case '[': {
                    if (skip_sep() == sep) {
                        save_and_next();
                        cont++;
                        if (LUA_COMPAT_LSTR == 1) {
                            if (sep == 0)
                                lexerror("nesting of [[...]] is deprecated", '[');
                        }
                    }
                    break;
                }
                case ']': {
                    if (skip_sep() == sep) {
                        save_and_next();
                        if (LUA_COMPAT_LSTR == 2) {
                            cont--;
                            if (sep == 0 && cont >= 0)
                                break;
                        }
                        endloop = true;
                    }
                    break;
                }
                case '\n':
                case '\r': {
                    save('\n');
                    inclinenumber();
                    if (seminfo == null && !inLexer)
                        nbuff = 0;
                    break;
                }
                default: {
                    if (seminfo != null || inLexer)
                        save_and_next();
                    else
                        nextChar();
                }
            }
        }
        if (seminfo != null)
            seminfo.ts = newstring(buff, 2 + sep, nbuff - 2 * (2 + sep));
        else if (inLexer)
            newstring(buff, 2 + sep, nbuff - 2 * (2 + sep));
    }

    int hexvalue(int c) {
        return c <= '9' ? c - '0' : c <= 'F' ? c + 10 - 'A' : c + 10 - 'a';
    }

    int readhexaesc() {
        nextChar();
        int c1 = current;
        nextChar();
        int c2 = current;
        if (!isxdigit(c1) || !isxdigit(c2))
            lexerror("hexadecimal digit expected 'x" + ((char) c1) + ((char) c2), TK_STRING);
        return (hexvalue(c1) << 4) + hexvalue(c2);
    }

    int readutf8aesc() {
        int i = 0;
        int c = 0;
        nextChar();
        do {
            c = (c << 4) + hexvalue(current);
            nextChar();
        } while (++i < 4 && isxdigit(current));
        save_utf8(c);
        return (char) c;
    }

    private int save_utf8(int ch) {
        if (ch < 0x80) {
            save((char) ch);
            return 1;
        }
        if (ch <= 0x7FF) {
            save((char) (ch >> 6) | 0xC0);
            save((char) (ch | 0x80) & 0xBF);
            return 2;
        }
        if (ch <= 0xFFFF) {
            save((char) (ch >> 12) | 0xE0);
            save((char) ((ch >> 6) | 0x80) & 0xBF);
            save((char) (ch | 0x80) & 0xBF);
            return 3;
        }
        return 0;
    }

    void read_string(int del, SemInfo seminfo) {
        save_and_next();
        while (current != del) {
            switch (current) {
                case EOZ:
                    lexerror("未完成的字符串", TK_EOS);
                    continue;
                case '\n':
                case '\r':
                    lexerror("未完成的字符串", TK_STRING);
                    continue;
                case '\\': {
                    int c;
                    nextChar();
                    switch (current) {
                        case 'a':
                            c = '\u0007';
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'v':
                            c = '\u000B';
                            break;
                        case 'u':
                            c = readutf8aesc();
                            continue;
                        case 'x':
                            c = readhexaesc();
                            break;
                        case '\n':
                        case '\r':
                            save('\n');
                            inclinenumber();
                            continue;
                        case EOZ:
                            continue;
                        case 'z': {
                            nextChar();
                            while (isspace(current)) {
                                if (currIsNewline()) inclinenumber();
                                else nextChar();
                            }
                            continue;
                        }
                        default: {
                            if (!isdigit(current))
                                save_and_next();
                            else {
                                int i = 0;
                                c = 0;
                                do {
                                    c = 10 * c + (current - '0');
                                    nextChar();
                                } while (++i < 3 && isdigit(current));
                                if (c > UCHAR_MAX)
                                    lexerror("escape sequence too large", TK_STRING);
                                save(c);
                            }
                            continue;
                        }
                    }
                    save(c);
                    nextChar();
                    continue;
                }
                default:
                    save_and_next();
            }
        }
        save_and_next();
        seminfo.ts = newstring(buff, 1, nbuff - 2);
    }

    int llex(SemInfo seminfo) {
        nbuff = 0;
        while (true) {
            switch (current) {
                case '\n':
                case '\r': {
                    inclinenumber();
                    continue;
                }
                case '-': {
                    nextChar();
                    if (current != '-')
                        return '-';
                    nextChar();
                    if (current == '[') {
                        int sep = skip_sep();
                        if (sep >= 0) {
                            read_long_string(null, sep);
                            nbuff = 0;
                            continue;
                        }
                    }
                    if (inLexer) {
                        while (!currIsNewline() && current != EOZ)
                            save_and_next();
                        newstring(buff, 0, nbuff);
                        nbuff = 0;
                    } else {
                        while (!currIsNewline() && current != EOZ)
                            nextChar();
                    }
                    continue;
                }
                case '[': {
                    int sep = skip_sep();
                    if (sep >= 0) {
                        read_long_string(seminfo, sep);
                        return TK_STRING;
                    } else if (sep == -1)
                        return '[';
                    else
                        lexerror("invalid long string delimiter", TK_STRING);
                    next();
                    continue;
                }
                case '=': {
                    nextChar();
                    if (current != '=')
                        return '=';
                    else {
                        nextChar();
                        return TK_EQ;
                    }
                }
                case '<': {
                    nextChar();
                    if (current == '<') {
                        nextChar();
                        return TK_SHL;
                    }
                    if (current != '=')
                        return '<';
                    else {
                        nextChar();
                        return TK_LE;
                    }
                }
                case '>': {
                    nextChar();
                    if (current == '>') {
                        nextChar();
                        return TK_SHR;
                    }
                    if (current != '=')
                        return '>';
                    else {
                        nextChar();
                        return TK_GE;
                    }
                }
                case '/': {
                    nextChar();
                    if (current != '/')
                        return '/';
                    else {
                        nextChar();
                        return TK_IDIV;
                    }
                }
                case '~': {
                    nextChar();
                    if (current != '=')
                        return '~';
                    else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case '!': {
                    nextChar();
                    if (current != '=')
                        return TK_NOT;
                    else {
                        nextChar();
                        return TK_NE;
                    }
                }
                case '$':{
                    nextChar();
                    return TK_LOCAL;
                }
                case '&': {
                    nextChar();
                    if (current != '&')
                        return '&';
                    else {
                        nextChar();
                        return TK_AND;
                    }
                }
                case '|': {
                    nextChar();
                    if (current != '|')
                        return '|';
                    else {
                        nextChar();
                        return TK_OR;
                    }
                }
                case '@':
                    nextChar();
                    return TK_DBCOLON;
                case ':': {
                    nextChar();
                    if (current != ':')
                        return ':';
                    else {
                        nextChar();
                        return TK_DBCOLON;
                    }
                }
                case '"':
                case '\'': {
                    read_string(current, seminfo);
                    return TK_STRING;
                }
                case '.': {
                    save_and_next();
                    if (check_next(".")) {
                        if (check_next("."))
                            return TK_DOTS;
                        else
                            return TK_CONCAT;
                    } else if (!isdigit(current))
                        return '.';
                    else {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    }
                }
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9': {
                    read_numeral(seminfo);
                    return TK_NUMBER;
                }
                case EOZ: {
                    return TK_EOS;
                }
                default: {
                    if (isspace(current)) {
                        _assert(!currIsNewline());
                        nextChar();
                        continue;
                    } else if (isdigit(current)) {
                        read_numeral(seminfo);
                        return TK_NUMBER;
                    } else if (isalpha(current) || current == '_') {
                        LuaString ts;
                        do {
                            save_and_next();
                        } while (isalnum(current));
                        ts = newstring(buff, 0, nbuff);
                        if (RESERVED.containsKey(ts))
                            return ((Integer) RESERVED.get(ts)).intValue();
                        else {
                            seminfo.ts = ts;
                            lastNameIdx=currentidx;
                            return TK_NAME;
                        }
                    } else {
                        int c = current;
                        nextChar();
                        return c;
                    }
                }
            }
        }
    }

    void next() {
        lastline = linenumber;
        lastidx = currentidx;
        lasttoken = t.token;
        if (lookahead.token != TK_EOS) {
            t.set(lookahead);
            lookahead.token = TK_EOS;
        } else
            t.token = llex(t.seminfo);
    }

    void lookahead() {
        _assert(lookahead.token == TK_EOS);
        lookahead.token = llex(lookahead.seminfo);
    }




    static final boolean vkisvar(final int k) {
        return (VLOCAL <= (k) && (k) <= VINDEXED);
    }

    static final boolean vkisinreg(final int k) {
        return ((k) == VNONRELOC || (k) == VLOCAL);
    }

    static class expdesc {
        public VarType type;
        int k;

        static class U {
            short ind_idx;
            short ind_t;
            short ind_vt;
            private LuaValue _nval;
            int info;

            public void setNval(LuaValue r) {
                _nval = r;
            }

            public LuaValue nval() {
                return (_nval == null ? LuaInteger.valueOf(info) : _nval);
            }
        }

        ;
        final U u = new U();
        final IntPtr t = new IntPtr();
        final IntPtr f = new IntPtr();

        void init(int k, int i) {
            this.f.i = NO_JUMP;
            this.t.i = NO_JUMP;
            this.k = k;
            this.u.info = i;
        }

        boolean hasjumps() {
            return (t.i != f.i);
        }

        boolean isnumeral() {
            return (k == VKNUM && t.i == NO_JUMP && f.i == NO_JUMP);
        }

        public void setvalue(expdesc other) {
            this.f.i = other.f.i;
            this.k = other.k;
            this.t.i = other.t.i;
            this.u._nval = other.u._nval;
            this.u.ind_idx = other.u.ind_idx;
            this.u.ind_t = other.u.ind_t;
            this.u.ind_vt = other.u.ind_vt;
            this.u.info = other.u.info;
            this.type = other.type;
        }

        protected expdesc clone() {
            expdesc c = new expdesc();
            c.setvalue(this);
            return c;
        }
    }


    static class Vardesc {
        final short idx;

        Vardesc(int idx) {
            this.idx = (short) idx;
        }
    }

    ;


    static class Labeldesc {
        LuaString name;
        int pc;
        int line;
        short nactvar;

        public Labeldesc(LuaString name, int pc, int line, short nactvar) {
            this.name = name;
            this.pc = pc;
            this.line = line;
            this.nactvar = nactvar;
        }
    }

    ;


    static class Dyndata {
        Vardesc[] actvar;
        int n_actvar = 0;
        Labeldesc[] gt;
        int n_gt = 0;
        Labeldesc[] label;
        int n_label = 0;
    }

    ;


    boolean hasmultret(int k) {
        return ((k) == VCALL || (k) == VVARARG);
    }


    void anchor_token() {
        _assert(fs != null || t.token == TK_EOS);
        if (t.token == TK_NAME || t.token == TK_STRING) {
            LuaString ts = t.seminfo.ts;
            L.cachedLuaString(t.seminfo.ts);
        }
    }

    void semerror(String msg) {
        t.token = 0;
        syntaxerror(msg);
    }

    void error_expected(int token) {
        syntaxerror(L.pushfstring(LUA_QS(token2str(token)) + " 预期"));
    }

    boolean testnext(int c) {
        if (t.token == c) {
            next();
            return true;
        } else
            return false;
    }

    boolean testtoken(int c) {
        if (t.token == c) {
            return true;
        } else
            return false;
    }

    void check(int c) {
        if (t.token != c)
            error_expected(c);
    }

    void checknext(int c) {
        check(c);
        next();
    }

    void check_condition(boolean c, String msg) {
        if (!(c))
            syntaxerror(msg);
    }


    void check_match(int what, int who, int where) {
        if (!testnext(what)) {
            if (where == linenumber)
                error_expected(what);
            else {
                errorline = where;
                syntaxerror(L.pushfstring(LUA_QS(token2str(what))
                        + " 预期 " + "(关闭 " + LUA_QS(token2str(who))
                        + " 在 " + where + " 行)"));
            }
        }
    }

    LuaString str_checkname() {
        LuaString ts;
        check(TK_NAME);
        ts = t.seminfo.ts;
        next();
        return ts;
    }

    LuaString str_check() {
        LuaString ts;
        check(TK_STRING);
        ts = t.seminfo.ts;
        next();
        return ts;
    }

    void codestring(expdesc e, LuaString s) {
        e.init(VK, fs.stringK(s));
    }

    void checkname(expdesc e) {
        codestring(e, str_checkname());
    }

    LuaString checkstring(expdesc e) {
        LuaString str = str_check();
        codestring(e, str);
        return str;
    }

    int registerlocalvar(LuaString varname) {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        if (f.locvars == null || fs.nlocvars + 1 > f.locvars.length)
            f.locvars = realloc(f.locvars, fs.nlocvars * 2 + 1);
        LocVars var = new LocVars(varname, 0, 0);
        f.locvars[fs.nlocvars] = var;
        if (inLexer) {
            addLexer(varname.tojstring(), VLOCAL);
        }
        return fs.nlocvars++;
    }

    void new_localvar(LuaString name) {
        int reg = registerlocalvar(name);
        fs.checklimit(dyd.n_actvar + 1, FuncState.LUAI_MAXVARS, "local variables");
        if (dyd.actvar == null || dyd.n_actvar + 1 > dyd.actvar.length)
            dyd.actvar = realloc(dyd.actvar, Math.max(1, dyd.n_actvar * 2));
        dyd.actvar[dyd.n_actvar++] = new Vardesc(reg);
    }

    int registerlocalvar(LuaString varname, VarType type) {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        if (f.locvars == null || fs.nlocvars + 1 > f.locvars.length)
            f.locvars = realloc(f.locvars, fs.nlocvars * 2 + 1);
        LocVars var = new LocVars(varname, 0, 0);
        var.type = type;
        f.locvars[fs.nlocvars] = var;
        if (inLexer) {
            var.startidx = this.lastidx;
            addLexer(varname.tojstring(), VLOCAL);
        }
        return fs.nlocvars++;
    }

    void new_localvar(LuaString name, VarType type) {
        int reg = registerlocalvar(name, type);
        fs.checklimit(dyd.n_actvar + 1, FuncState.LUAI_MAXVARS, "local variables");
        if (dyd.actvar == null || dyd.n_actvar + 1 > dyd.actvar.length)
            dyd.actvar = realloc(dyd.actvar, Math.max(1, dyd.n_actvar * 2));
        dyd.actvar[dyd.n_actvar++] = new Vardesc(reg);
    }

    void new_localvarliteral(String v) {
        LuaString ts = newstring(v);
        new_localvar(ts);
    }

    void adjustlocalvars(int nvars) {
        FuncState fs = this.fs;
        fs.nactvar = (short) (fs.nactvar + nvars);
        for (; nvars > 0; nvars--) {
            LocVars var = fs.getlocvar(fs.nactvar - nvars);
            var.startpc = fs.pc;
        }
    }

    void removevars(int tolevel) {
        FuncState fs = this.fs;
        while (fs.nactvar > tolevel)
            fs.getlocvar(--fs.nactvar).endpc = fs.pc;
    }

    public static ArrayList<LuaString> globals = new ArrayList<>();
    public static HashMap<String, ArrayList<Pair>> valueMap = new HashMap<>();

    private void addLexer(String n, int vartype) {
        ArrayList<Pair> a = valueMap.get(n);
        if (a == null) {
            a = new ArrayList<>();
            valueMap.put(n, a);
        }
        a.add(new Pair(lastidx, vartype));
    }

    void singlevar(expdesc var) {
        LuaString varname = this.str_checkname();
        FuncState fs = this.fs;
        int vartype = FuncState.singlevaraux(fs, varname, var, 1);
        if (inLexer) {
            addLexer(varname.tojstring(), vartype);
        }

        if (vartype != VLOCAL && Lua.LUA_FUNC_ENV) {
            if (varname.eq_b(this.envn)) {
                var.init(VENV, NO_REG);
                return;
            }
        }

        if (vartype == VVOID) {
            if (inLexer)
                globals.add(varname);
            expdesc key = new expdesc();
            vartype = FuncState.singlevaraux(fs, this.envn, var, 1);
            if (Lua.LUA_FUNC_ENV && vartype != VLOCAL) {
                var.init(VGLOBAL, fs.stringK(varname));
                var.u.info = fs.stringK(varname);
            } else {
                _assert(var.k == VLOCAL || var.k == VUPVAL);
                this.codestring(key, varname);
                fs.indexed(var, key);
            }
        }
    }

    void adjust_assign(int nvars, int nexps, expdesc e) {
        FuncState fs = this.fs;
        int extra = nvars - nexps;
        if (hasmultret(e.k)) {
            extra++;
            if (extra < 0)
                extra = 0;
            fs.setreturns(e, extra);
            if (extra > 1)
                fs.reserveregs(extra - 1);
        } else {
            if (e.k != VVOID)
                fs.exp2nextreg(e);
            if (extra > 0) {
                int reg = fs.freereg;
                fs.reserveregs(extra);
                fs.nil(reg, extra);
            }
        }
    }

    void enterlevel() {
        if (++L.nCcalls > LUAI_MAXCCALLS)
            lexerror("chunk has too many syntax levels", 0);
    }

    void leavelevel() {
        L.nCcalls--;
    }

    void closegoto(int g, Labeldesc label) {
        FuncState fs = this.fs;
        Labeldesc[] gl = this.dyd.gt;
        Labeldesc gt = gl[g];
        _assert(gt.name.eq_b(label.name));
        if (gt.nactvar < label.nactvar) {
            LuaString vname = fs.getlocvar(gt.nactvar).varname;
            String msg = L.pushfstring("<goto " + gt.name + "> at line "
                    + gt.line + " jumps into the scope of local '"
                    + vname.tojstring() + "'");
            semerror(msg);
        }
        fs.patchlist(gt.pc, label.pc);
        System.arraycopy(gl, g + 1, gl, g, this.dyd.n_gt - g - 1);
        gl[--this.dyd.n_gt] = null;
    }

    boolean findlabel(int g) {
        int i;
        BlockCnt bl = fs.bl;
        Dyndata dyd = this.dyd;
        Labeldesc gt = dyd.gt[g];
        for (i = bl.firstlabel; i < dyd.n_label; i++) {
            Labeldesc lb = dyd.label[i];
            if (lb.name.eq_b(gt.name)) {
                if (gt.nactvar > lb.nactvar &&
                        (bl.upval || dyd.n_label > bl.firstlabel))
                    fs.patchclose(gt.pc, lb.nactvar);
                closegoto(g, lb);
                return true;
            }
        }
        return false;
    }

    int newlabelentry(Labeldesc[] l, int index, LuaString name, int line, int pc) {
        l[index] = new Labeldesc(name, pc, line, fs.nactvar);
        return index;
    }

    void findgotos(Labeldesc lb) {
        Labeldesc[] gl = dyd.gt;
        int i = fs.bl.firstgoto;
        while (i < dyd.n_gt) {
            if (gl[i].name.eq_b(lb.name))
                closegoto(i, lb);
            else
                i++;
        }
    }


    void breaklabel() {
        LuaString n = LuaString.valueOf("break");
        int l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, n, 0, fs.pc);
        findgotos(dyd.label[l]);
    }

    void continuelabel() {
        LuaString n = LuaString.valueOf("continue");
        int l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, n, 0, fs.pc);
        findgotos(dyd.label[l]);
    }

    void undefgoto(Labeldesc gt) {
        String msg = L.pushfstring(isReservedKeyword(gt.name.tojstring())
                ? "<" + gt.name + "> at line " + gt.line + " not inside a loop"
                : "no visible label '" + gt.name + "' for <goto> at line " + gt.line);
        semerror(msg);
    }

    Prototype addprototype() {
        Prototype clp;
        Prototype f = fs.f;
        if (f.p == null || fs.np >= f.p.length) {
            f.p = realloc(f.p, Math.max(1, fs.np * 2));
        }
        f.p[fs.np++] = clp = new Prototype();
        return clp;
    }

    void codeclosure(expdesc v) {
        FuncState fs = this.fs.prev;
        v.init(VRELOCABLE, fs.codeABx(OP_CLOSURE, 0, fs.np - 1));
        fs.exp2nextreg(v);
    }

    void open_func(FuncState fs, BlockCnt bl) {
        fs.prev = this.fs;
        fs.ls = this;
        this.fs = fs;
        fs.pc = 0;
        fs.lasttarget = -1;
        fs.jpc = new IntPtr(NO_JUMP);
        fs.freereg = 0;
        fs.nk = 0;
        fs.np = 0;
        fs.nups = 0;
        fs.nlocvars = 0;
        fs.nactvar = 0;
        fs.firstlocal = dyd.n_actvar;
        fs.bl = null;
        fs.f.source = this.source;
        fs.f.maxstacksize = 2;
        fs.enterblock(bl, false);
    }

    void close_func() {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        fs.ret(0, 0);
        fs.leaveblock();
        f.code = realloc(f.code, fs.pc);
        f.lineinfo = realloc(f.lineinfo, fs.pc);
        f.k = realloc(f.k, fs.nk);
        f.p = realloc(f.p, fs.np);
        f.locvars = realloc(f.locvars, fs.nlocvars);
        f.upvalues = realloc(f.upvalues, fs.nups);
        _assert(fs.bl == null);
        this.fs = fs.prev;
    }


    void fieldsel(expdesc v) {
        FuncState fs = this.fs;
        expdesc key = new expdesc();
        fs.exp2anyregup(v);
        this.next();
        this.checkname(key);
        fs.indexed(v, key);
    }

    void yindex(expdesc v) {
        this.next();
        this.expr(v);
        this.fs.exp2val(v);
        this.checknext(']');
    }




    static class ConsControl {
        expdesc v = new expdesc();
        expdesc t;
        int nh;
        int na;
        int tostore;
    }

    ;


    void recfield(ConsControl cc) {
        FuncState fs = this.fs;
        int reg = this.fs.freereg;
        expdesc key = new expdesc();
        expdesc val = new expdesc();
        int rkkey;
        boolean isstr = false;
        boolean isfunc = false;
        if (this.t.token == TK_FUNCTION) {
            isfunc = true;
            fs.checklimit(cc.nh, MAX_INT, "构造函数中的项");
            next();
            this.checkname(key);
        } else if (this.t.token == TK_STRING) {
            isstr = true;
            fs.checklimit(cc.nh, MAX_INT, "构造函数中的项");
            this.checkstring(key);
        } else if (this.t.token == TK_NAME) {
            fs.checklimit(cc.nh, MAX_INT, "构造函数中的项");
            this.checkname(key);
        } else if (this.t.token == TK_NUMBER) {
            isstr = true;
            fs.checklimit(cc.nh, MAX_INT, "构造函数中的项");
            key.init(VKNUM, 0);
            key.u.setNval(this.t.seminfo.r);
            next();
        } else
            this.yindex(key);
        cc.nh++;
        if (!isfunc && (!isstr || !testnext(':')))
            this.checknext('=');
        rkkey = fs.exp2RK(key);
        if (isfunc)
            this.body(val, false, this.linenumber);
        else
            this.expr(val);
        fs.codeABC(Lua.OP_SETTABLE, cc.t.u.info, rkkey, fs.exp2RK(val));
        fs.freereg = (short) reg;
    }

    void listfield(ConsControl cc) {
        this.expr(cc.v);
        fs.checklimit(cc.na, MAX_INT, "构造函数中的项");
        cc.na++;
        cc.tostore++;
    }


    void constructor(expdesc t) {
        int startidx = currentidx;
        int startline = linenumber;
        FuncState fs = this.fs;
        int line = this.linenumber;
        int pc = fs.codeABC(Lua.OP_NEWTABLE, 0, 0, 0);
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0);
        fs.exp2nextreg(t);
        this.checknext('{');
        do {
            _assert(cc.v.k == VVOID || cc.tostore > 0);
            if (this.t.token == '}')
                break;
            fs.closelistfield(cc);
            switch (this.t.token) {
                case TK_STRING:
                    this.lookahead();
                    if (this.lookahead.token != '=' && this.lookahead.token != ':')
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;

                case TK_FUNCTION:
                    this.lookahead();
                    if (this.lookahead.token != TK_NAME)
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;
                case TK_NUMBER:
                case TK_NAME: {
                    this.lookahead();
                    if (this.lookahead.token != '=')
                        this.listfield(cc);
                    else
                        this.recfield(cc);
                    break;
                }
                case '[': {
                    this.recfield(cc);
                    break;
                }
                default: {
                    this.listfield(cc);
                    break;
                }
            }
        } while (this.testnext(',') || this.testnext(';'));
        if (inLexer) {
            lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
        }
        this.check_match('}', '{', line);
        fs.lastlistfield(cc);
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        SETARG_B(i, luaO_int2fb(cc.na));
        SETARG_C(i, luaO_int2fb(cc.nh));
    }

    void constructorList(expdesc t) {
        FuncState fs = this.fs;
        int line = this.linenumber;
        int pc = fs.codeABx(Lua.OP_NEWLIST, 0, 0);
        ConsControl cc = new ConsControl();
        cc.na = cc.nh = cc.tostore = 0;
        cc.t = t;
        t.init(VRELOCABLE, pc);
        cc.v.init(VVOID, 0);
        fs.exp2nextreg(t);
        this.checknext('[');
        do {
            _assert(cc.v.k == VVOID || cc.tostore > 0);
            if (this.t.token == ']')
                break;
            fs.closelistfield(cc);
            this.listfield(cc);
        } while (this.testnext(',') || this.testnext(';'));
        this.check_match(']', '[', line);
        fs.lastlistfield(cc);
        InstructionPtr i = new InstructionPtr(fs.f.code, pc);
        SETARG_B(i, luaO_int2fb(cc.na));
    }

    static int luaO_int2fb(int x) {
        int e = 0;
        while (x >= 16) {
            x = (x + 1) >> 1;
            e++;
        }
        if (x < 8) return x;
        else return ((e + 1) << 3) | (((int) x) - 8);
    }



    void parlist() {
        FuncState fs = this.fs;
        Prototype f = fs.f;
        int nparams = 0;
        f.is_vararg = 0;
        if (this.t.token != ')') {
            do {
                switch (this.t.token) {
                    case TK_NAME: {
                        this.new_localvar(this.str_checkname());
                        ++nparams;
                        break;
                    }
                    case TK_DOTS: {
                        this.next();
                        f.is_vararg = 1;
                        break;
                    }
                    default:
                        this.syntaxerror("<name> or " + LUA_QL("...") + " 预期");
                }
            } while ((f.is_vararg == 0) && this.testnext(','));
        }
        this.adjustlocalvars(nparams);
        f.numparams = fs.nactvar;
        fs.reserveregs(fs.nactvar);
    }

    void body(expdesc e, boolean needself, int line) {
        int startidx = currentidx;
        int startline = linenumber;
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.startidx = currentidx;
        open_func(new_fs, bl);
        this.checknext('(');
        if (needself) {
            new_localvarliteral("self");
            adjustlocalvars(1);
        }
        this.parlist();
        this.checknext(')');
        boolean left = CURLY&&this.testnext('{');
        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        this.statlist();
        new_fs.f.endidx = currentidx;
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left)
            this.check_match('}', TK_FUNCTION, line);
        else
            this.check_match(TK_END, TK_FUNCTION, line);
    }

    void lambdabody(expdesc e, int line) {
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.startidx = currentidx;
        open_func(new_fs, bl);
        boolean left = this.testnext('(');
        this.parlist();
        if (left)
            this.checknext(')');

        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        this.checknext(':');
        retstat();
        new_fs.f.endidx = currentidx;
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
    }

    void deferbody(expdesc e, int line) {
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.startidx = currentidx;
        open_func(new_fs, bl);
        new_fs.f.name = "defer";
        boolean hasarg = this.testnext('(');
        if (hasarg) {
            this.parlist();
            this.checknext(')');
        }
        this.statement();
        new_fs.f.endidx = currentidx;
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
    }

    void whenbody(expdesc e, int line) {
        int startidx = currentidx;
        int startline = linenumber;
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.startidx = currentidx;
        open_func(new_fs, bl);
        new_fs.f.name = "when";
        boolean left = CURLY&&this.testnext('(');
        expdesc control = new expdesc();
        expr(control);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }

        IntPtr escapelist = new IntPtr(NO_JUMP);

        while (t.token == TK_CASE) {
            test_case_block(escapelist, control.clone());
        }
        if (testnext(TK_DEFAULT))
            block();

        fs.patchtohere(escapelist.i);

        new_fs.f.endidx = currentidx;
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left)
            this.check_match('}', TK_WHEN, line);
        else
            this.check_match(TK_END, TK_WHEN, line);
    }

    boolean trybody(expdesc e, int token, int line) {
        FuncState new_fs = new FuncState();
        BlockCnt bl = new BlockCnt();
        new_fs.f = addprototype();
        new_fs.f.linedefined = line;
        new_fs.f.startidx = currentidx;
        open_func(new_fs, bl);
        if (token == TK_CATCH) {
            boolean hasarg = this.testnext('(');
            if (hasarg) {
                this.parlist();
                this.checknext(')');
            }
        }
        boolean left = CURLY&&this.testnext('{');
        this.statlist();
        new_fs.f.endidx = currentidx;
        new_fs.f.lastlinedefined = this.linenumber;
        this.codeclosure(e);
        this.close_func();
        if (left)
            check_match('}', token, line);
        return left;
    }

    int explist(expdesc v) {
        int n = 1;
        this.expr(v);
        while (this.testnext(',')) {
            fs.exp2nextreg(v);
            this.expr(v);
            n++;
        }
        return n;
    }


    void funcargs(expdesc f, int line) {
        FuncState fs = this.fs;
        expdesc args = new expdesc();
        int base, nparams;
        switch (this.t.token) {
            case '(': {
                this.next();
                if (this.t.token == ')')
                    args.k = VVOID;
                else {
                    this.explist(args);
                    fs.setmultret(args);
                }
                this.check_match(')', '(', line);
                break;
            }
            case '{': {
                this.constructor(args);
                break;
            }
            case TK_STRING: {
                this.codestring(args, this.t.seminfo.ts);
                this.next();
                break;
            }
            default: {
                this.syntaxerror("需要函数参数");
                return;
            }
        }
        _assert(f.k == VNONRELOC);
        base = f.u.info;
        if (hasmultret(args.k))
            nparams = Lua.LUA_MULTRET;
        else {
            if (args.k != VVOID)
                fs.exp2nextreg(args);
            nparams = fs.freereg - (base + 1);
        }
        f.init(VCALL, fs.codeABC(Lua.OP_CALL, base, nparams + 1, 2));
        fs.fixline(line);
        fs.freereg = (short) (base + 1);
    }



    void primaryexp(expdesc v) {
        switch (t.token) {
            case '(': {
                int line = linenumber;
                this.next();
                this.expr(v);
                this.check_match(')', '(', line);
                fs.dischargevars(v);
                return;
            }
            case TK_NAME: {
                singlevar(v);
                return;
            }
            default: {
                this.syntaxerror("意外的符号 " + t.token);
                return;
            }
        }
    }


    void suffixedexp(expdesc v) {
        int line = linenumber;
        primaryexp(v);
        for (; ; ) {
            switch (t.token) {
                case '.': {
                    this.fieldsel(v);
                    break;
                }
                case '[': {
                    expdesc key = new expdesc();
                    fs.exp2anyregup(v);
                    this.yindex(key);
                    fs.indexed(v, key);
                    break;
                }
                case ':': {
                    expdesc key = new expdesc();
                    this.next();
                    this.checkname(key);
                    fs.self(v, key);
                    this.funcargs(v, line);
                    break;
                }
                case '(':
                case TK_STRING:
                case '{': {
                    fs.exp2nextreg(v);
                    this.funcargs(v, line);
                    break;
                }
                default:
                    return;
            }
        }
    }


    void simpleexp(expdesc v) {
        switch (this.t.token) {
            case TK_NUMBER: {
                v.init(VKNUM, 0);
                v.u.setNval(this.t.seminfo.r);
                v.type = VarType.TNUMBER;
                break;
            }
            case TK_STRING: {
                this.codestring(v, this.t.seminfo.ts);
                v.type = VarType.TSTRING;
                break;
            }
            case TK_NIL: {
                v.init(VNIL, 0);
                v.type = VarType.TNIL;
                break;
            }
            case TK_TRUE: {
                v.init(VTRUE, 0);
                v.type = VarType.TBOOLEAN;
                break;
            }
            case TK_FALSE: {
                v.init(VFALSE, 0);
                v.type = VarType.TBOOLEAN;
                break;
            }
            case TK_DOTS: {
                FuncState fs = this.fs;
                this.check_condition(fs.f.is_vararg != 0, "cannot use " + LUA_QL("...")
                        + " outside a vararg function");
                v.init(VVARARG, fs.codeABC(Lua.OP_VARARG, 0, 1, 0));
                break;
            }
            case '{': {
                this.constructor(v);
                v.type = VarType.TTABLE;
                return;
            }
            case '[': {
                this.constructorList(v);
                v.type = VarType.TTABLE;
                return;
            }
            case TK_FUNCTION: {
                this.next();
                this.body(v, false, this.linenumber);
                v.type = VarType.TFUNCTION;
                return;
            }
            case TK_LAMBDA: {
                this.next();
                this.lambdabody(v, this.linenumber);
                return;
            }
            case TK_WHEN: {
                v.init(VCALL, whenstat());
                return;
            }
            case TK_IMPORT: {
                this.importstat(v);
                break;
            }
            default: {
                this.suffixedexp(v);
                return;
            }
        }
        this.next();
    }


    int getunopr(int op) {
        switch (op) {
            case TK_NOT:
                return OPR_NOT;
            case '-':
                return OPR_MINUS;
            case '#':
                return OPR_LEN;
            case '~':
                return OPR_BNOT;
            default:
                return OPR_NOUNOPR;
        }
    }


    int getbinopr(int op) {
        switch (op) {
            case '+':
                return OPR_ADD;
            case '-':
                return OPR_SUB;
            case '*':
                return OPR_MUL;
            case '/':
                return OPR_DIV;
            case '%':
                return OPR_MOD;
            case '^':
                return OPR_POW;
            case TK_CONCAT:
                return OPR_CONCAT;
            case TK_NE:
                return OPR_NE;
            case TK_EQ:
                return OPR_EQ;
            case '<':
                return OPR_LT;
            case TK_LE:
                return OPR_LE;
            case '>':
                return OPR_GT;
            case TK_GE:
                return OPR_GE;
            case TK_AND:
                return OPR_AND;
            case TK_OR:
                return OPR_OR;
            case TK_IDIV:
                return OPR_IDIV;
            case '&':
                return OPR_BAND;
            case '|':
                return OPR_BOR;
            case '~':
                return OPR_BXOR;
            case TK_SHL:
                return OPR_SHL;
            case TK_SHR:
                return OPR_SHR;
            default:
                return OPR_NOBINOPR;
        }
    }

    static class Priority {
        final byte left;

        final byte right;

        public Priority(int i, int j) {
            left = (byte) i;
            right = (byte) j;
        }
    }


    static Priority[] priority = {
            new Priority(10, 10), new Priority(10, 10),
            new Priority(11, 11), new Priority(11, 11),
            new Priority(14, 13),
            new Priority(11, 11), new Priority(11, 11),
            new Priority(6, 6), new Priority(4, 4), new Priority(5, 5),
            new Priority(7, 7), new Priority(7, 7),
            new Priority(9, 8),
            new Priority(3, 3), new Priority(3, 3), new Priority(3, 3),
            new Priority(3, 3), new Priority(3, 3), new Priority(3, 3),
            new Priority(2, 2), new Priority(1, 1)
    };

    static final int UNARY_PRIORITY = 8;


    int subexpr(expdesc v, int limit) {
        int op;
        int uop;
        this.enterlevel();
        uop = getunopr(this.t.token);
        if (uop != OPR_NOUNOPR) {
            int line = linenumber;
            this.next();
            this.subexpr(v, UNARY_PRIORITY);
            fs.prefix(uop, v, line);
        } else {
            this.simpleexp(v);
        }
        op = getbinopr(this.t.token);
        while (op != OPR_NOBINOPR && priority[op].left > limit) {
            expdesc v2 = new expdesc();
            int line = linenumber;
            this.next();
            fs.infix(op, v);
            int nextop = this.subexpr(v2, priority[op].right);
            fs.posfix(op, v, v2, line);
            op = nextop;
        }
        this.leavelevel();
        return op;
    }

    void expr(expdesc v) {
        this.subexpr(v, 0);
    }





    boolean block_follow(boolean withuntil) {
        switch (t.token) {
            case TK_ELSE:
            case TK_ELSEIF:
            case TK_END:
            case TK_EOS:
            case TK_CASE:
            case TK_DEFAULT:
            case TK_CATCH:
            case TK_FINALLY:
            case '}':
                return true;
            case TK_UNTIL:
                return withuntil;
            default:
                return false;
        }
    }


    void block() {
        FuncState fs = this.fs;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, false);
        this.statlist();
        fs.leaveblock();
    }


    static class LHS_assign {
        LHS_assign prev;
        expdesc v = new expdesc();
    }

    ;


    void check_conflict(LHS_assign lh, expdesc v) {
        FuncState fs = this.fs;
        short extra = (short) fs.freereg;
        boolean conflict = false;
        for (; lh != null; lh = lh.prev) {
            if (lh.v.k == VINDEXED) {
                if (lh.v.u.ind_vt == v.k && lh.v.u.ind_t == v.u.info) {
                    conflict = true;
                    lh.v.u.ind_vt = VLOCAL;
                    lh.v.u.ind_t = extra;
                }
                if (v.k == VLOCAL && lh.v.u.ind_idx == v.u.info) {
                    conflict = true;
                    lh.v.u.ind_idx = extra;
                }
            }
        }
        if (conflict) {
            int op = (v.k == VLOCAL) ? Lua.OP_MOVE : Lua.OP_GETUPVAL;
            fs.codeABC(op, extra, v.u.info, 0);
            fs.reserveregs(1);
        }
    }

    void assignment(LHS_assign lh, int nvars) {
        expdesc e = new expdesc();
        this.check_condition(VLOCAL <= lh.v.k && lh.v.k <= VINDEXED,
                "语法错误");
        if (this.testnext(',')) {
            LHS_assign nv = new LHS_assign();
            nv.prev = lh;
            this.suffixedexp(nv.v);
            if (nv.v.k != VINDEXED)
                this.check_conflict(lh, nv.v);
            this.assignment(nv, nvars + 1);
        } else {
            int nexps;
            this.checknext('=');
            nexps = this.explist(e);
            if (nexps != nvars) {
                this.adjust_assign(nvars, nexps, e);
                if (nexps > nvars)
                    this.fs.freereg -= nexps - nvars;
            } else {
                fs.setoneret(e);
                fs.storevar(lh.v, e);
                return;
            }
        }
        e.init(VNONRELOC, this.fs.freereg - 1);
        fs.storevar(lh.v, e);
    }


    int cond() {
        expdesc v = new expdesc();
        this.expr(v);
        if (v.k == VNIL)
            v.k = VFALSE;
        fs.goiftrue(v);
        return v.f.i;
    }

    void gotostat(int pc) {
        int line = linenumber;
        LuaString label;
        int g;
        if (testnext(TK_GOTO))
            label = str_checkname();
        else if (testnext(TK_CONTINUE))
            label = LuaString.valueOf("continue");
        else {
            next();
            label = LuaString.valueOf("break");
        }
        g = newlabelentry(dyd.gt = grow(dyd.gt, dyd.n_gt + 1), dyd.n_gt++, label, line, pc);
        findlabel(g);
    }


    void skipnoopstat() {
        while (t.token == ';' || t.token == TK_DBCOLON)
            statement();
    }


    void labelstat(LuaString label, int line) {
        int l;
        fs.checkrepeated(dyd.label, dyd.n_label, label);
        testnext(TK_DBCOLON);
        l = newlabelentry(dyd.label = grow(dyd.label, dyd.n_label + 1), dyd.n_label++, label, line, fs.getlabel());
        skipnoopstat();
        if (block_follow(false)) {
            dyd.label[l].nactvar = fs.bl.nactvar;
        }
        findgotos(dyd.label[l]);
    }

    void whilestat(int line) {
        int startidx = currentidx;
        int startline = linenumber;
        FuncState fs = this.fs;
        int whileinit;
        int condexit;
        BlockCnt bl = new BlockCnt();
        this.next();
        boolean left = CURLY&&this.testnext('(');
        whileinit = fs.getlabel();
        condexit = this.cond();
        fs.enterblock(bl, true);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        this.block();
        continuelabel();
        fs.patchlist(fs.jump(), whileinit);
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left)
            this.check_match('}', TK_WHILE, line);
        else
            this.check_match(TK_END, TK_WHILE, line);
        fs.leaveblock();
        fs.patchtohere(condexit);
    }

    void repeatstat(int line) {
        int startidx = currentidx;
        int startline = linenumber;
        int condexit;
        FuncState fs = this.fs;
        boolean left = CURLY&&this.testnext('{');
        int repeat_init = fs.getlabel();
        BlockCnt bl1 = new BlockCnt();
        BlockCnt bl2 = new BlockCnt();
        fs.enterblock(bl1, true);
        fs.enterblock(bl2, false);
        this.next();
        this.statlist();
        continuelabel();
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left) {
            this.check_match('}', TK_REPEAT, line);
        }
        this.check_match(TK_UNTIL, TK_REPEAT, line);
        condexit = this.cond();
        if (bl2.upval) {
            fs.patchclose(condexit, bl2.nactvar);
        }
        fs.leaveblock();
        fs.patchlist(condexit, repeat_init);
        fs.leaveblock();
    }


    int exp1() {
        expdesc e = new expdesc();
        int k;
        this.expr(e);
        k = e.k;
        fs.exp2nextreg(e);
        return k;
    }


    boolean forbody(int base, int line, int nvars, boolean isnum, boolean left) {
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep, endfor;
        this.adjustlocalvars(3);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        prep = isnum ? fs.codeAsBx(Lua.OP_FORPREP, base, NO_JUMP) : fs.jump();
        fs.enterblock(bl, false);
        this.adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        this.block();
        continuelabel();
        fs.leaveblock();
        fs.patchtohere(prep);
        if (isnum)
            endfor = fs.codeAsBx(Lua.OP_FORLOOP, base, NO_JUMP);
        else {
            fs.codeABC(Lua.OP_TFORCALL, base, 0, nvars);
            fs.fixline(line);
            endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, NO_JUMP);
        }
        fs.patchlist(endfor, prep + 1);
        fs.fixline(line);
        return left;
    }

    boolean foreachbody(int base, int line, int nvars, boolean left) {
        BlockCnt bl = new BlockCnt();
        FuncState fs = this.fs;
        int prep, endfor;
        this.adjustlocalvars(3);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        prep = fs.jump();
        fs.enterblock(bl, false);
        this.adjustlocalvars(nvars);
        fs.reserveregs(nvars);
        this.block();
        continuelabel();
        fs.leaveblock();
        fs.patchtohere(prep);
        fs.codeABC(Lua.OP_TFOREACH, base, 0, nvars);
        fs.fixline(line);
        endfor = fs.codeAsBx(Lua.OP_TFORLOOP, base + 2, NO_JUMP);
        fs.patchlist(endfor, prep + 1);
        fs.fixline(line);
        return left;
    }

    boolean fornum(LuaString varname, int line, boolean left) {
        FuncState fs = this.fs;
        int base = fs.freereg;
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_INDEX);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_LIMIT);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STEP);
        this.new_localvar(varname);
        this.checknext('=');
        this.exp1();
        this.checknext(',');
        this.exp1();
        if (this.testnext(','))
            this.exp1();
        else {
            fs.codeABx(Lua.OP_LOADK, fs.freereg, fs.numberK(LuaInteger.valueOf(1)));
            fs.reserveregs(1);
        }
        return this.forbody(base, line, 1, true, left);
    }


    boolean forlist(LuaString indexname, boolean left) {
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int nvars = 4;
        int line;
        int base = fs.freereg;
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_GENERATOR);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STATE);
        this.new_localvarliteral(RESERVED_LOCAL_VAR_FOR_CONTROL);
        this.new_localvar(indexname);
        while (this.testnext(',')) {
            this.new_localvar(this.str_checkname());
            ++nvars;
        }
        boolean foreach = this.testnext(':');
        this.testnext(TK_IN);
        line = this.linenumber;
        this.adjust_assign(3, this.explist(e), e);
        fs.checkstack(3);
        if (foreach)
            return this.foreachbody(base, line, nvars - 3, left);
        else
            return this.forbody(base, line, nvars - 3, false, left);
    }


    void forstat(int line) {
        int startidx = currentidx;
        int startline = linenumber;
        FuncState fs = this.fs;
        LuaString varname;
        BlockCnt bl = new BlockCnt();
        fs.enterblock(bl, true);
        this.next();
        boolean left = CURLY&&this.testnext('(');
        varname = this.str_checkname();
        switch (this.t.token) {
            case '=':
                left = this.fornum(varname, line, left);
                break;
            case ',':
            case TK_IN:
                left = this.forlist(varname, left);
                break;
            default:
                left = this.forlist(varname, left);
        }
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left)
            this.check_match('}', TK_FOR, line);
        else
            this.check_match(TK_END, TK_FOR, line);
        fs.leaveblock();
    }


    boolean test_then_block(IntPtr escapelist) {
        expdesc v = new expdesc();
        BlockCnt bl = new BlockCnt();
        int jf = 0;
        int token = t.token;
        int line = this.linenumber;
        this.next();
        boolean left = CURLY&&this.testnext('(');
        expr(v);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_THEN);
        }
        if (t.token == TK_GOTO || t.token == TK_BREAK || t.token == TK_CONTINUE) {
            fs.goiffalse(v);
            fs.enterblock(bl, false);
            gotostat(v.t.i);
            skipnoopstat();
            if (block_follow(false)) {
                fs.leaveblock();
                return left;
            } else
                syntaxerror("unreachable statement");
        } else {
            fs.goiftrue(v);
            fs.enterblock(bl, false);
            jf = v.f.i;
        }
        statlist();
        fs.leaveblock();
        if (left)
            this.check_match('}', token, line);
        if (t.token == TK_ELSE || t.token == TK_ELSEIF)
            fs.concat(escapelist, fs.jump());
        fs.patchtohere(jf);
        return left;
    }


    void ifstat(int line) {
        int startidx = currentidx;
        int startline = linenumber;
        boolean left = false;
        IntPtr escapelist = new IntPtr(NO_JUMP);
        left = test_then_block(escapelist);
        while (t.token == TK_ELSEIF)
            left = test_then_block(escapelist);
        if (testnext(TK_ELSE)) {
            left = CURLY&&this.testnext('{');
            block();
            if (left)
                check_match('}', TK_IF, line);
        }
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, lastidx - 1, lastline));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (!left)
            check_match(TK_END, TK_IF, line);
        fs.patchtohere(escapelist.i);
    }

    void test_case_block(IntPtr escapelist, expdesc control) {
        expdesc v = new expdesc();
        BlockCnt bl = new BlockCnt();
        int jf = 0;
        this.next();
        expdesc gcontrol = control.clone();
        enterlevel();
        fs.infix(OPR_EQ, control);
        expr(v);
        fs.posfix(OPR_EQ, control, v, linenumber);
        while (testnext(',')) {
            expdesc c = gcontrol.clone();
            fs.infix(OPR_EQ, c);
            expr(v);
            fs.posfix(OPR_EQ, c, v, linenumber);
            fs.infix(OPR_OR, control);
            fs.posfix(OPR_OR, control, c, linenumber);
        }
        leavelevel();
        this.testnext(TK_THEN);

        if (t.token == TK_GOTO || t.token == TK_BREAK || t.token == TK_CONTINUE) {
            fs.goiffalse(control);
            fs.enterblock(bl, false);
            gotostat(control.t.i);
            skipnoopstat();
            if (block_follow(false)) {
                fs.leaveblock();
                return;
            } else
                syntaxerror("unreachable statement");
        } else {
            fs.goiftrue(control);
            fs.enterblock(bl, false);
            jf = control.f.i;
        }
        statlist();
        fs.leaveblock();
        if (t.token == TK_CASE || t.token == TK_DEFAULT)
            fs.concat(escapelist, fs.jump());
        fs.patchtohere(jf);
    }

    void switchstat(int line) {
        int startidx = currentidx;
        int startline = linenumber;
        IntPtr escapelist = new IntPtr(NO_JUMP);
        expdesc control = new expdesc();
        this.next();
        boolean left = CURLY&&this.testnext('(');
        expr(control);
        if (left) {
            this.checknext(')');
            left = this.testnext('{');
        }
        if (!left) {
            this.testnext(TK_DO);
        }
        while (t.token == TK_CASE) {
            test_case_block(escapelist, control.clone());
        }
        if (testnext(TK_DEFAULT))
            block();
        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, currentidx - 1, linenumber));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }
        if (left)
            check_match('}', TK_SWITCH, line);
        else
            check_match(TK_END, TK_SWITCH, line);
        fs.patchtohere(escapelist.i);

    }

    void localfunc() {
        expdesc b = new expdesc();
        FuncState fs = this.fs;
        this.new_localvar(this.str_checkname());
        this.adjustlocalvars(1);
        this.body(b, false, this.linenumber);
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
    }

    private HashMap<String, VarType> classCache = new HashMap<>();

    void importstat() {
        this.next();
        FuncState fs = this.fs;
        boolean left = this.testnext('(');
        do {
            String varname = null;
            if (t.token == TK_NAME) {
                varname = this.str_checkname().tojstring();
            }
            LuaString classname = this.str_check();
            if (varname == null) {
                varname = classname.tojstring();
                int idx = varname.lastIndexOf(".");
                if (idx > 0)
                    varname = varname.substring(idx + 1);
            }
            classCache.put(varname, new VarType(classname.tojstring()));
            this.new_localvar(LuaString.valueOf(varname), new VarType(classname.tojstring()));
            this.adjustlocalvars(1);
            fs.codeABx(Lua.OP_IMPORT, fs.nactvar - 1, fs.stringK(classname));
            fs.reserveregs(1);
        } while (this.testnext(','));
        if (left)
            this.checknext(')');
    }

    void importstat(expdesc e) {
        this.next();
        FuncState fs = this.fs;
        LuaString classname = this.str_check();
        String varname = classname.tojstring();
        int idx = varname.lastIndexOf(".");
        if (idx > 0) {
            String pkgname = varname.substring(0, idx);
            varname = varname.substring(idx + 1);
            if (varname.equals("*")) {
                e.init(VRELOCABLE, fs.codeABx(Lua.OP_LOADK, e.u.info, fs.stringK(LuaValue.valueOf(pkgname))));
                return;
            }
        }
        e.init(VRELOCABLE, fs.codeABx(Lua.OP_IMPORT, e.u.info, fs.stringK(classname)));
    }

    void modulestat() {
        this.next();
        FuncState fs = this.fs;
        boolean left = this.testnext('(');
        LuaString classname = this.str_check();
        this.new_localvar(this.envn);
        this.adjustlocalvars(1);
        fs.codeABx(Lua.OP_MODULE, fs.nactvar - 1, fs.stringK(classname));
        fs.reserveregs(1);
        if (left)
            this.checknext(')');
    }

    void deferstat() {
        this.next();
        FuncState fs = this.fs;
        expdesc b = new expdesc();
        this.new_localvarliteral("(defer)");
        this.adjustlocalvars(1);
        this.deferbody(b, this.linenumber);
        fs.codeABC(Lua.OP_DEFER, fs.nactvar - 1, 0, 0);
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
    }

    int whenstat() {
        this.next();
        FuncState fs = this.fs;
        expdesc b = new expdesc();
        this.new_localvarliteral("(when)");
        this.adjustlocalvars(1);
        this.whenbody(b, this.linenumber);
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        return fs.codeABC(Lua.OP_CALL, fs.nactvar - 1, 0, 2);
    }

    void whenstat(expdesc b) {
        FuncState fs = this.fs;
        this.new_localvarliteral("(when)");
        this.adjustlocalvars(1);
        this.whenbody(b, this.linenumber);
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        b.init(VCALL, fs.codeABC(Lua.OP_CALL, fs.nactvar - 1, 0, 0));
    }

    void trystat() {
        int startidx = currentidx;
        int startline = linenumber;
        int ra = 0, rb = 0, rc = 0;
        boolean left = false;
        int line = this.linenumber;
        this.next();
        expdesc b = new expdesc();
        FuncState fs = this.fs;
        ra = fs.freereg;
        this.new_localvarliteral("(try)");
        this.adjustlocalvars(1);
        left = this.trybody(b, TK_TRY, this.linenumber);
        fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;

        if (this.testnext(TK_CATCH)) {
            rb = fs.freereg;
            this.new_localvarliteral("(catch)");
            this.adjustlocalvars(1);
            left = this.trybody(b, TK_CATCH, this.linenumber);
            fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        }

        if (this.testnext(TK_FINALLY)) {
            rc = fs.freereg;
            this.new_localvarliteral("(finally)");
            this.adjustlocalvars(1);
            left = this.trybody(b, TK_FINALLY, this.linenumber);
            fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
        }

        if (inLexer) {
            if (left)
                lines.add(new Rect(startidx, startline, lastidx - 1, lastline));
            else
                lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
        }

        if (!left)
            check_match(TK_END, TK_TRY, line);

        fs.codeABC(Lua.OP_TCALL, ra, rb, rc);
    }

    void loadlist(int n) {
        LocVars[] ls = fs.f.locvars;
        expdesc var = new expdesc();
        int nl = fs.nlocvars;
        LuaString l = ls[nl - n].varname;
        FuncState.singlevaraux(fs, l, var, 1);
        for (int i = nl - n + 1; i < nl; i++) {
            l = ls[i].varname;
            fs.exp2nextreg(var);
            FuncState.singlevaraux(fs, l, var, 1);
        }
        this.adjust_assign(n, n, var);
        this.adjustlocalvars(n);
    }

    void localstat() {
        int nvars = 0;
        int nexps;
        VarType type = null;
        expdesc e = new expdesc();
        if (testnext('<')) {
            String tn = this.str_checkname().tojstring();
            if (classCache.containsKey(tn))
                type = classCache.get(tn);
            else
                type = new VarType(tn);
            checknext('>');
        }
        boolean def = testnext('=') || testnext(':');
        do {
            this.new_localvar(this.str_checkname(), type);
            ++nvars;
        } while (this.testnext(','));

        if (def) {
            loadlist(nvars);
            return;
        }

        if (nvars == 1 && testtoken('(')) {
            expdesc b = new expdesc();
            this.adjustlocalvars(1);
            this.body(b, false, this.linenumber);
            fs.getlocvar(fs.nactvar - 1).startpc = fs.pc;
            return;
        }

        if (this.testnext('='))
            nexps = this.explist(e, nvars, type);
        else {
            e.k = VVOID;
            nexps = 0;
        }
        this.adjust_assign(nvars, nexps, e);
        this.adjustlocalvars(nvars);
    }

    int explist(expdesc v, int nvars, VarType type) {
        int n = 1;
        this.expr(v);
        if (type == null)
            fs.getlocvar(fs.nactvar + n - 1).type = v.type;
        else
            checktype(fs.getlocvar(fs.nactvar + n - 1).varname, type, v.type);
        while (this.testnext(',')) {
            fs.exp2nextreg(v);
            this.expr(v);
            n++;
            if (type == null)
                fs.getlocvar(fs.nactvar + n - 1).type = v.type;
            else
                checktype(fs.getlocvar(fs.nactvar + n - 1).varname, type, v.type);
        }
        return n;
    }

    @SuppressLint("DefaultLocale")
    public void checktype(LuaString varname, VarType t1, VarType t2) {
        if (t1 == null||t2 == null||t2.type==LuaValue.TNIL)
            return;
        if (!t1.typename.equals(fs.typename(t2))) {
            LexState.errormsg = String.format("%d: 当地的 %s 类型错误 %s %s", lastline, varname, fs.typename(t1), fs.typename(t2));
        }
    }

    boolean funcname(expdesc v) {
        boolean ismethod = false;
        this.singlevar(v);
        while (this.t.token == '.')
            this.fieldsel(v);
        if (this.t.token == ':') {
            ismethod = true;
            this.fieldsel(v);
        }
        return ismethod;
    }


    void funcstat(int line) {
        boolean needself;
        expdesc v = new expdesc();
        expdesc b = new expdesc();
        this.next();
        needself = this.funcname(v);
        this.body(b, needself, line);
        fs.storevar(v, b);
        fs.fixline(line);
    }


    void exprstat() {
        FuncState fs = this.fs;
        LHS_assign v = new LHS_assign();
        this.suffixedexp(v.v);
        if (t.token == '=' || t.token == ',') {
            v.prev = null;
            assignment(v, 1);
        } else {
            check_condition(v.v.k == VCALL, "语法错误");
            if (v.v.k == VCALL)
                SETARG_C(fs.getcodePtr(v.v), 1);
        }
    }

    void retstat() {
        FuncState fs = this.fs;
        expdesc e = new expdesc();
        int first, nret;
        if (block_follow(true) || this.t.token == ';')
            first = nret = 0;
        else {
            nret = this.explist(e);
            if (hasmultret(e.k)) {
                fs.setmultret(e);
                if (e.k == VCALL && nret == 1) {
                    SET_OPCODE(fs.getcodePtr(e), Lua.OP_TAILCALL);
                    _assert(Lua.GETARG_A(fs.getcode(e)) == fs.nactvar);
                }
                first = fs.nactvar;
                nret = Lua.LUA_MULTRET;
            } else {
                if (nret == 1)
                    first = fs.exp2anyreg(e);
                else {
                    fs.exp2nextreg(e);
                    first = fs.nactvar;
                    _assert(nret == fs.freereg - first);
                }
            }
        }
        fs.ret(first, nret);
        testnext(';');
    }

    void statement() {
        int line = this.linenumber;
        enterlevel();
        switch (this.t.token) {
            case ';': {
                next();
                break;
            }
            case TK_IF: {
                this.ifstat(line);
                break;
            }
            case TK_WHILE: {
                this.whilestat(line);
                break;
            }
            case TK_SWITCH: {
                this.switchstat(line);
                break;
            }
            case TK_DO: {
                int startidx = currentidx;
                int startline = linenumber;
                this.next();
                this.block();
                if (inLexer) {
                    lines.add(new Rect(startidx, startline, currentidx - 3, linenumber));
                }
                this.check_match(TK_END, TK_DO, line);

                break;
            }
            case TK_FOR: {
                this.forstat(line);
                break;
            }
            case TK_REPEAT: {
                this.repeatstat(line);
                break;
            }
            case TK_FUNCTION: {
                this.funcstat(line);
                break;
            }
            case TK_IMPORT: {
                this.importstat();
                break;
            }
            case TK_MODULE: {
                this.modulestat();
                break;
            }
            case TK_DEFER: {
                this.deferstat();
                break;
            }
            case TK_WHEN: {
                this.whenstat();
                break;
            }
            case TK_TRY: {
                this.trystat();
                break;
            }
            case TK_LOCAL: {
                this.next();
                if (this.testnext(TK_FUNCTION))
                    this.localfunc();
                else
                    this.localstat();
                break;
            }
            case TK_DBCOLON: {
                next();
                labelstat(str_checkname(), line);
                break;
            }
            case TK_RETURN: {
                next();
                this.retstat();
                break;
            }
            case TK_CONTINUE:
            case TK_BREAK: {
                gotostat(fs.jump());
                if (!block_follow(true))
                    syntaxerror("unreachable statement");
                break;
            }
            case TK_GOTO: {
                this.gotostat(fs.jump());
                break;
            }
            default: {
                try {
                    this.exprstat();
                } catch (Exception e) {
                    next();
                }
                break;
            }
        }
        if (!(fs.f.maxstacksize >= fs.freereg
                && fs.freereg >= fs.nactvar))
            syntaxerror("statement");
        fs.freereg = fs.nactvar;
        leavelevel();
    }

    private int errorCount = 0;

    void statlist() {
        while (!block_follow(true)) {
            if(abort.isSet()) {
                errormsg=null;
                return;
            }
            if (t.token == TK_RETURN) {
                statement();
                return;
            }
            statement();

        }
    }

    public static ArrayList<Pair> tokens = new ArrayList<>();
    public static ArrayList<Rect> lines = new ArrayList<>();

    public void mainfunc(FuncState funcstate) {
        if (inLexer) {
            globals.clear();
            valueMap.clear();
            tokens.clear();
            lines.clear();
            errormsg = null;
            errorline = -1;
            erroridx = -1;
            errorCount = 0;
        }
        BlockCnt bl = new BlockCnt();
        open_func(funcstate, bl);
        fs.f.is_vararg = 1;
        expdesc v = new expdesc();
        v.init(VLOCAL, 0);
        fs.newupvalue(envn, v);
        if (Lua.LUA_LOCAL_ENV) {
            this.new_localvar(this.envn);
            expdesc env = new expdesc();
            FuncState.singlevaraux(fs, this.envn, env, 1);
            this.adjust_assign(1, 1, env);
            this.adjustlocalvars(1);
        }
        next();
        if (testtoken('{') || testtoken(TK_STRING))
            retstat();
        else
            statlist();
        check(TK_EOS);
        close_func();
        if (inLexer) {
            for (Rect line : lines) {
                line.top -= 1;
                line.bottom -= 1;
            }
        }
    }


}
