#if !defined LUA_VERSION_NUM
#define luaL_Reg luaL_reg

#define luaL_addchar(B,c) \
  ((void)((B)->p < ((B)->buffer+LUAL_BUFFERSIZE) || luaL_prepbuffer(B)), \
   (*(B)->p++ = (char)(c)))
#endif

#if LUA_VERSION_NUM==501
#define lua_rawlen lua_objlen
#endif

void luaL_setfuncs (lua_State *L, const luaL_Reg *l, int nup);
