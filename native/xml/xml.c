





#if defined __WIN32__ || defined WIN32
# include <windows.h>
# define _EXPORT __declspec(dllexport)
#else
# define _EXPORT
#endif

#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdlib.h>

static const char ESC=27;
static const char OPN=28;
static const char CLS=29;


static const char* char2code(unsigned char ch, char buf[8]) {
	unsigned char i=0;
	buf[i++]='&';
	buf[i++]='#';
	if(ch>99) buf[i++]=ch/100+48;
	if(ch>9) buf[i++]=(ch%100)/10+48;
	buf[i++]=ch%10+48;
	buf[i++]=';';
	buf[i]=0;
	return buf;
}

static size_t find(const char* s, const char* pattern, size_t start) {
	const char* found =strstr(s+start, pattern);
	return found ? found-s : strlen(s);
}


typedef struct Tokenizer_s  {
	const char* s;
	size_t s_size;
	size_t i;
	int tagMode;
	const char* m_next;
	size_t m_next_size;
	char* m_token;
	size_t m_token_size;
	size_t m_token_capacity;
} Tokenizer;

Tokenizer* Tokenizer_new(const char* str, size_t str_size) {
	Tokenizer *tok = (Tokenizer*)malloc(sizeof(Tokenizer));
	memset(tok, 0, sizeof(Tokenizer));
	tok->s_size = str_size;
	tok->s = str;
	return tok;
}

void Tokenizer_delete(Tokenizer* tok) {
	free(tok->m_token);
	free(tok);
}


static const char* Tokenizer_set(Tokenizer* tok, const char* s, size_t size) {
	if(!size||!s) return 0;
	free(tok->m_token);
	tok->m_token = (char*)malloc(size+1);
	strncpy(tok->m_token,s, size);
	tok->m_token[size] = 0;
	tok->m_token_size = tok->m_token_capacity = size;
	return tok->m_token;
}

static void Tokenizer_append(Tokenizer* tok, char ch) {
	if(tok->m_token_size+1>=tok->m_token_capacity) {
		tok->m_token_capacity = (tok->m_token_capacity==0) ? 16 : tok->m_token_capacity*2;
		tok->m_token = (char*)realloc(tok->m_token, tok->m_token_capacity);
	}
	tok->m_token[tok->m_token_size]=ch;
	tok->m_token[++tok->m_token_size]=0;
}

const char* Tokenizer_next(Tokenizer* tok) {
	const char* ESC_str = "\033";
	const char* OPEN_str = "\034";
	const char* CLOSE_str = "\035";

	
	if(tok->m_token) {
		free(tok->m_token);
		tok->m_token = 0;
		tok->m_token_size=tok->m_token_capacity = 0;
	}
	
	int quotMode=0;
	int tokenComplete = 0;
	while(tok->m_next_size || (tok->i < tok->s_size)) {

		if(tok->m_next_size) {
			Tokenizer_set(tok, tok->m_next, tok->m_next_size);
			tok->m_next=0;
			tok->m_next_size=0;
			return tok->m_token;
		}

		switch(tok->s[tok->i]) {
		    case '"':
		    case '\'':
			if(tok->tagMode) {
				if(!quotMode) quotMode=tok->s[tok->i];
				else if(quotMode==tok->s[tok->i]) quotMode=0;
			}
			Tokenizer_append(tok, tok->s[tok->i]);
			break;
		    case '<':
			if(!quotMode&&(tok->i+4<tok->s_size)&&(strncmp(tok->s+tok->i,"<!--",4)==0))
			    tok->i=find(tok->s, "-->", tok->i+4)+2;
			else if(!quotMode&&(tok->i+9<tok->s_size)&&(strncmp(tok->s+tok->i,"<![CDATA[",9)==0)) {
			    size_t b=tok->i+9;
			    tok->i=find(tok->s, "]]>",b)+3;
			    if(!tok->m_token_size) return Tokenizer_set(tok, tok->s+b, tok->i-b-3);
			    tokenComplete = 1;
			    tok->m_next = tok->s+b;
			    tok->m_next_size = tok->i-b-3;
			    --tok->i;
			}
			else if(!quotMode&&(tok->i+1<tok->s_size)&&((tok->s[tok->i+1]=='?')||(tok->s[tok->i+1]=='!')))
			    tok->i=find(tok->s, ">", tok->i+2);
			else if(!quotMode&&!tok->tagMode) {
				if((tok->i+1<tok->s_size)&&(tok->s[tok->i+1]=='/')) {
					tok->m_next=ESC_str;
					tok->m_next_size = 1;
					tok->i=find(tok->s, ">", tok->i+2);
				}
				else {
					tok->m_next = OPEN_str;
					tok->m_next_size = 1;
					tok->tagMode=1;
				}
				tokenComplete = 1;
			}
			else Tokenizer_append(tok, tok->s[tok->i]);
			break;
		    case '/':
			if(tok->tagMode&&!quotMode) {
				tokenComplete = 1;
				if((tok->i+1 < tok->s_size) && (tok->s[tok->i+1]=='>')) {
					tok->tagMode=0;
					tok->m_next=ESC_str;
					tok->m_next_size = 1;
					++tok->i;
				}
				else Tokenizer_append(tok, tok->s[tok->i]);
			}
			else Tokenizer_append(tok, tok->s[tok->i]);
			break;
		    case '>':
			if(!quotMode&&tok->tagMode) {
				tok->tagMode=0;
				tokenComplete = 1;
				tok->m_next = CLOSE_str;
				tok->m_next_size = 1;
			}
			else Tokenizer_append(tok, tok->s[tok->i]);
			break;
		    case ' ':
		    case '\r':
		    case '\n':
		    case '\t':
			if(tok->tagMode&&!quotMode) {
			    if(tok->m_token_size) tokenComplete=1;
			}
			else if(tok->m_token_size) Tokenizer_append(tok, tok->s[tok->i]);
			break;
		    default: Tokenizer_append(tok, tok->s[tok->i]);
		}
		++tok->i;
		if((tok->i>=tok->s_size)||(tokenComplete&&tok->m_token_size)) {
			tokenComplete=0;
			while(tok->m_token_size&&isspace(tok->m_token[tok->m_token_size-1]))
				tok->m_token[--tok->m_token_size]=0;
			if(tok->m_token_size) break;
		}
	}
	return tok->m_token;
}


static size_t sv_code_size=0;
static size_t sv_code_capacity=16;
static char** sv_code=0;


static void Xml_pushDecode(lua_State* L, const char* s, size_t s_size) {
	size_t start=0, pos;
	if(!s_size) s_size=strlen(s);
	luaL_Buffer b;
	luaL_buffinit(L, &b);
	const char* found = strstr(s, "&#");
	if(!found) pos = s_size;
	else pos = found-s;
	while(found&&(pos+5<s_size)&&(*(found+5)==';')&&isdigit(*(found+2))&&isdigit(*(found+3))&&isdigit(*(found+4)) ) {
		if(pos>start) luaL_addlstring(&b,s+start, pos-start);
		luaL_addchar(&b, 100*(s[pos+2]-48)+10*(s[pos+3]-48)+(s[pos+4]-48));
		start=pos+6;
		found = strstr(found+6, "&#");
		if(!found) pos = s_size;
		else pos = found-s;
	}
	if(pos>start) luaL_addlstring(&b,s+start, pos-start);
	luaL_pushresult(&b);
	size_t i;
	for(i=sv_code_size-1; i<sv_code_size; i-=2) {
		luaL_gsub(L, lua_tostring(L,-1), sv_code[i], sv_code[i-1]);
		lua_remove(L,-2);
	}
}

int Xml_eval(lua_State *L) {
	char* str = 0;
	size_t str_size=0;
	if(lua_isuserdata(L,1)) str = (char*)lua_touserdata(L,1);
	else {
		const char * sTmp = luaL_checklstring(L,1,&str_size);
		str = (char*)malloc(str_size+1);
		memcpy(str, sTmp, str_size);
		str[str_size]=0;
	}
	Tokenizer* tok = Tokenizer_new(str, str_size ? str_size : strlen(str));
	lua_settop(L,0);
	const char* token=0;
	int firstStatement = 1;
	while((token=Tokenizer_next(tok))!=0) if(token[0]==OPN) {
		if(lua_gettop(L)) {
			int newIndex=lua_objlen(L,-1)+1;
			lua_pushnumber(L,newIndex);
			lua_newtable(L);
			lua_settable(L, -3);
			lua_pushnumber(L,newIndex);
			lua_gettable(L,-2);
		}
		else {
			if (firstStatement) {
				lua_newtable(L);
				firstStatement = 0;
			}
			else return lua_gettop(L);
		}
		lua_newtable(L);
		lua_pushliteral(L, "__index");
		lua_getglobal(L, "xml");
		lua_settable(L, -3);
			
		lua_pushliteral(L, "__tostring");
		lua_getglobal(L, "xml");
		lua_pushliteral(L,"str");
		lua_gettable(L, -2);
		lua_remove(L, -2);
		lua_settable(L, -3);
		lua_setmetatable(L, -2);
		
		lua_pushnumber(L,0);
		lua_pushstring(L, Tokenizer_next(tok));
		lua_settable(L, -3);
		
		while(((token = Tokenizer_next(tok))!=0)&&(token[0]!=CLS)&&(token[0]!=ESC)) {
			size_t sepPos=find(token, "=", 0);
			if(token[sepPos]) {
				const char* aVal =token+sepPos+2;
				lua_pushlstring(L, token, sepPos);
				Xml_pushDecode(L, aVal, strlen(aVal)-1);
				lua_settable(L, -3);
			}
		}
		if(!token||(token[0]==ESC)) {
			if(lua_gettop(L)>1) lua_settop(L,-2);
			else break;
		}
	}
	else if(token[0]==ESC) {
		if(lua_gettop(L)>1) lua_settop(L,-2);
		else break;
	}
	else {
		lua_pushnumber(L,lua_objlen(L,-1)+1);
		Xml_pushDecode(L, token, 0);
		lua_settable(L, -3);
	}
	Tokenizer_delete(tok);
	free(str);
	return lua_gettop(L);
}

int Xml_load (lua_State *L) {
	const char * filename = luaL_checkstring(L,1);
	FILE * file=fopen(filename,"r");
	if(!file) return luaL_error(L,"LuaXml ERROR: \"%s\" file error or file not found!",filename);

	fseek (file , 0 , SEEK_END);
	size_t sz = ftell (file);
	rewind (file);
	char* buffer = (char*)malloc(sz+1);
	fread (buffer,1,sz,file);
	fclose(file);
	buffer[sz]=0;
	lua_pushlightuserdata(L,buffer);
	lua_replace(L,1);
	return Xml_eval(L);
};

int Xml_registerCode(lua_State *L) {
    const char * decoded = luaL_checkstring(L,1);
    const char * encoded = luaL_checkstring(L,2);
    
    size_t i;
    for(i=0; i<sv_code_size; i+=2)
        if(strcmp(sv_code[i],decoded)==0)
            return luaL_error(L,"LuaXml ERROR: code already exists.");
	if(sv_code_size+2>sv_code_capacity) {
		sv_code_capacity*=2;
		sv_code = (char**)realloc(sv_code, sv_code_capacity*sizeof(char*));
	}
	sv_code[sv_code_size]=(char*)malloc(strlen(decoded)+1);
	strcpy(sv_code[sv_code_size++], decoded);
	sv_code[sv_code_size]=(char*)malloc(strlen(encoded)+1);
	strcpy(sv_code[sv_code_size++],encoded);
    return 0;
}

int Xml_encode(lua_State *L) {
	if(lua_gettop(L)!=1) return 0;
	luaL_checkstring(L,-1);
	size_t i;
	for(i=0; i<sv_code_size; i+=2) {
		luaL_gsub(L, lua_tostring(L,-1), sv_code[i], sv_code[i+1]);
		lua_remove(L,-2);
	}
	char buf[8];
	const char* s=lua_tostring(L,1);
	size_t start, pos;
	luaL_Buffer b;
	luaL_buffinit(L, &b);
	for(start=pos=0; s[pos]!=0; ++pos) if(s[pos]<0) {
		if(pos>start) luaL_addlstring(&b,s+start, pos-start);
		luaL_addstring(&b,char2code((unsigned char)(s[pos]),buf));
		start=pos+1;
	}
	if(pos>start) luaL_addlstring(&b,s+start, pos-start);
	luaL_pushresult(&b);
	lua_remove(L,-2);
    return 1;
}

int _EXPORT luaopen_xml_core (lua_State* L) {
	static const struct luaL_reg funcs[] = {
		{"load", Xml_load},
		{"eval", Xml_eval},
		{"encode", Xml_encode},
		{"registerCode", Xml_registerCode},
		{NULL, NULL}
	};
	luaL_newlib(L, funcs);
	if(!sv_code) {
		sv_code=(char**)malloc(sv_code_capacity*sizeof(char*));
		sv_code[sv_code_size++]="&";
		sv_code[sv_code_size++]="&amp;";
		sv_code[sv_code_size++]="<";
		sv_code[sv_code_size++]="&lt;";
		sv_code[sv_code_size++]=">";
		sv_code[sv_code_size++]="&gt;";
		sv_code[sv_code_size++]="\"";
		sv_code[sv_code_size++]="&quot;";
		sv_code[sv_code_size++]="'";
		sv_code[sv_code_size++]="&apos;";
	}
	return 1;
}
