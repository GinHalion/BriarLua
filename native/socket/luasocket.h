#ifndef LUASOCKET_H
#define LUASOCKET_H
#include "lua.h"

#define LUASOCKET_VERSION    "LuaSocket 3.0-rc1"
#define LUASOCKET_COPYRIGHT  "Copyright (C) 1999-2013 Diego Nehab"

#ifndef LUASOCKET_API
#define LUASOCKET_API extern
#endif

LUASOCKET_API int luaopen_socket_core(lua_State *L);

#endif
