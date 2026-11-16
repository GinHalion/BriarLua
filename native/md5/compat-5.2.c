#include "lua.h"
#include "lauxlib.h"
#include "compat-5.2.h"

#if !defined LUA_VERSION_NUM || LUA_VERSION_NUM==501
void luaL_setfuncs (lua_State *L, const luaL_Reg *l, int nup) {
  luaL_checkstack(L, nup+1, "too many upvalues");
  for (; l->name != NULL; l++) {
    int i;
    lua_pushstring(L, l->name);
    for (i = 0; i < nup; i++)
      lua_pushvalue(L, -(nup + 1));
    lua_pushcclosure(L, l->func, nup);
    lua_settable(L, -(nup + 3));
  }
  lua_pop(L, nup);
}
#endif
