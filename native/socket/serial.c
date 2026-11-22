#include <string.h>

#include "lua.h"
#include "lauxlib.h"

#include "auxiliar.h"
#include "socket.h"
#include "options.h"
#include "unix.h"
#include <sys/un.h>




static int global_create(lua_State *L);
static int meth_send(lua_State *L);
static int meth_receive(lua_State *L);
static int meth_close(lua_State *L);
static int meth_settimeout(lua_State *L);
static int meth_getfd(lua_State *L);
static int meth_setfd(lua_State *L);
static int meth_dirty(lua_State *L);
static int meth_getstats(lua_State *L);
static int meth_setstats(lua_State *L);

static luaL_Reg serial_methods[] = {
    {"__gc",        meth_close},
    {"__tostring",  auxiliar_tostring},
    {"close",       meth_close},
    {"dirty",       meth_dirty},
    {"getfd",       meth_getfd},
    {"getstats",    meth_getstats},
    {"setstats",    meth_setstats},
    {"receive",     meth_receive},
    {"send",        meth_send},
    {"setfd",       meth_setfd},
    {"settimeout",  meth_settimeout},
    {NULL,          NULL}
};

static luaL_Reg func[] = {
    {"serial", global_create},
    {NULL,          NULL}
};


LUASOCKET_API int luaopen_socket_serial(lua_State *L) {
    auxiliar_newclass(L, "serial{client}", serial_methods);
    auxiliar_add2group(L, "serial{client}", "serial{any}");
#if LUA_VERSION_NUM > 501 && !defined(LUA_COMPAT_MODULE)
    lua_pushcfunction(L, global_create);
    (void) func;
#else
    luaL_openlib(L, "socket", func, 0);
    lua_pushcfunction(L, global_create);
#endif
    return 1;
}

static int meth_send(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkclass(L, "serial{client}", 1);
    return buffer_meth_send(L, &un->buf);
}

static int meth_receive(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkclass(L, "serial{client}", 1);
    return buffer_meth_receive(L, &un->buf);
}

static int meth_getstats(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkclass(L, "serial{client}", 1);
    return buffer_meth_getstats(L, &un->buf);
}

static int meth_setstats(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkclass(L, "serial{client}", 1);
    return buffer_meth_setstats(L, &un->buf);
}

static int meth_getfd(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkgroup(L, "serial{any}", 1);
    lua_pushnumber(L, (int) un->sock);
    return 1;
}

static int meth_setfd(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkgroup(L, "serial{any}", 1);
    un->sock = (t_socket) luaL_checknumber(L, 2);
    return 0;
}

static int meth_dirty(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkgroup(L, "serial{any}", 1);
    lua_pushboolean(L, !buffer_isempty(&un->buf));
    return 1;
}

static int meth_close(lua_State *L)
{
    p_unix un = (p_unix) auxiliar_checkgroup(L, "serial{any}", 1);
    socket_destroy(&un->sock);
    lua_pushnumber(L, 1);
    return 1;
}


static int meth_settimeout(lua_State *L) {
    p_unix un = (p_unix) auxiliar_checkgroup(L, "serial{any}", 1);
    return timeout_meth_settimeout(L, &un->tm);
}



static int global_create(lua_State *L) {
    const char* path = luaL_checkstring(L, 1);

    p_unix un = (p_unix) lua_newuserdata(L, sizeof(t_unix));

    t_socket sock = open(path, O_NOCTTY|O_RDWR);


    if (sock < 0)  {
        lua_pushnil(L);
        lua_pushstring(L, socket_strerror(errno));
        lua_pushnumber(L, errno);
        return 3;
    }
    auxiliar_setclass(L, "serial{client}", -1);
    socket_setnonblocking(&sock);
    un->sock = sock;
    io_init(&un->io, (p_send) socket_write, (p_recv) socket_read,
            (p_error) socket_ioerror, &un->sock);
    timeout_init(&un->tm, -1, -1);
    buffer_init(&un->buf, &un->io, &un->tm);
    return 1;
}
