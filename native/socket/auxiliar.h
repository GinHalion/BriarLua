#ifndef AUXILIAR_H
#define AUXILIAR_H

#include "lua.h"
#include "lauxlib.h"

int auxiliar_open(lua_State *L);
void auxiliar_newclass(lua_State *L, const char *classname, luaL_Reg *func);
void auxiliar_add2group(lua_State *L, const char *classname, const char *group);
void auxiliar_setclass(lua_State *L, const char *classname, int objidx);
void *auxiliar_checkclass(lua_State *L, const char *classname, int objidx);
void *auxiliar_checkgroup(lua_State *L, const char *groupname, int objidx);
void *auxiliar_getclassudata(lua_State *L, const char *groupname, int objidx);
void *auxiliar_getgroupudata(lua_State *L, const char *groupname, int objidx);
int auxiliar_checkboolean(lua_State *L, int objidx);
int auxiliar_tostring(lua_State *L);
int auxiliar_typeerror(lua_State *L, int narg, const char *tname);

#endif
