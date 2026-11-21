
#include "lua.h"
#include "lauxlib.h"

#include "luasocket.h"
#include "auxiliar.h"
#include "except.h"
#include "timeout.h"
#include "buffer.h"
#include "inet.h"
#include "tcp.h"
#include "udp.h"
#include "select.h"

static int global_skip(lua_State *L);
static int global_unload(lua_State *L);
static int base_open(lua_State *L);

static const luaL_Reg mod[] = {
    {"auxiliar", auxiliar_open},
    {"except", except_open},
    {"timeout", timeout_open},
    {"buffer", buffer_open},
    {"inet", inet_open},
    {"tcp", tcp_open},
    {"udp", udp_open},
    {"select", select_open},
    {NULL, NULL}
};

static luaL_Reg func[] = {
    {"skip",      global_skip},
    {"__unload",  global_unload},
    {NULL,        NULL}
};

static int global_skip(lua_State *L) {
    int amount = luaL_checkinteger(L, 1);
    int ret = lua_gettop(L) - amount - 1;
    return ret >= 0 ? ret : 0;
}

static int global_unload(lua_State *L) {
    (void) L;
    socket_close();
    return 0;
}

static int base_open(lua_State *L) {
    if (socket_open()) {
#if LUA_VERSION_NUM > 501 && !defined(LUA_COMPAT_MODULE)
        lua_newtable(L);
        luaL_setfuncs(L, func, 0);
#else
        luaL_openlib(L, "socket", func, 0);
#endif
#ifdef LUASOCKET_DEBUG
        lua_pushstring(L, "_DEBUG");
        lua_pushboolean(L, 1);
        lua_rawset(L, -3);
#endif
        lua_pushstring(L, "_VERSION");
        lua_pushstring(L, LUASOCKET_VERSION);
        lua_rawset(L, -3);
        return 1;
    } else {
        lua_pushstring(L, "unable to initialize library");
        lua_error(L);
        return 0;
    }
}

LUASOCKET_API int luaopen_socket_core(lua_State *L) {
    int i;
    base_open(L);
    for (i = 0; mod[i].name; i++) mod[i].func(L);
    return 1;
}
