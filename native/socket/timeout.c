#include <stdio.h>
#include <limits.h>
#include <float.h>

#include "lua.h"
#include "lauxlib.h"

#include "auxiliar.h"
#include "timeout.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#include <sys/time.h>
#endif

#ifndef MIN
#define MIN(x, y) ((x) < (y) ? x : y)
#endif
#ifndef MAX
#define MAX(x, y) ((x) > (y) ? x : y)
#endif

static int timeout_lua_gettime(lua_State *L);
static int timeout_lua_sleep(lua_State *L);

static luaL_Reg func[] = {
    { "gettime", timeout_lua_gettime },
    { "sleep", timeout_lua_sleep },
    { NULL, NULL }
};

void timeout_init(p_timeout tm, double block, double total) {
    tm->block = block;
    tm->total = total;
}

double timeout_get(p_timeout tm) {
    if (tm->block < 0.0 && tm->total < 0.0) {
        return -1;
    } else if (tm->block < 0.0) {
        double t = tm->total - timeout_gettime() + tm->start;
        return MAX(t, 0.0);
    } else if (tm->total < 0.0) {
        return tm->block;
    } else {
        double t = tm->total - timeout_gettime() + tm->start;
        return MIN(tm->block, MAX(t, 0.0));
    }
}

double timeout_getstart(p_timeout tm) {
    return tm->start;
}

double timeout_getretry(p_timeout tm) {
    if (tm->block < 0.0 && tm->total < 0.0) {
        return -1;
    } else if (tm->block < 0.0) {
        double t = tm->total - timeout_gettime() + tm->start;
        return MAX(t, 0.0);
    } else if (tm->total < 0.0) {
        double t = tm->block - timeout_gettime() + tm->start;
        return MAX(t, 0.0);
    } else {
        double t = tm->total - timeout_gettime() + tm->start;
        return MIN(tm->block, MAX(t, 0.0));
    }
}

p_timeout timeout_markstart(p_timeout tm) {
    tm->start = timeout_gettime();
    return tm;
}

#ifdef _WIN32
double timeout_gettime(void) {
    FILETIME ft;
    double t;
    GetSystemTimeAsFileTime(&ft);
    t  = ft.dwLowDateTime/1.0e7 + ft.dwHighDateTime*(4294967296.0/1.0e7);
    return (t - 11644473600.0);
}
#else
double timeout_gettime(void) {
    struct timeval v;
    gettimeofday(&v, (struct timezone *) NULL);
    return v.tv_sec + v.tv_usec/1.0e6;
}
#endif

int timeout_open(lua_State *L) {
#if LUA_VERSION_NUM > 501 && !defined(LUA_COMPAT_MODULE)
    luaL_setfuncs(L, func, 0);
#else
    luaL_openlib(L, NULL, func, 0);
#endif
    return 0;
}

int timeout_meth_settimeout(lua_State *L, p_timeout tm) {
    double t = luaL_optnumber(L, 2, -1);
    const char *mode = luaL_optstring(L, 3, "b");
    switch (*mode) {
        case 'b':
            tm->block = t;
            break;
        case 'r': case 't':
            tm->total = t;
            break;
        default:
            luaL_argcheck(L, 0, 3, "invalid timeout mode");
            break;
    }
    lua_pushnumber(L, 1);
    return 1;
}

static int timeout_lua_gettime(lua_State *L)
{
    lua_pushnumber(L, timeout_gettime());
    return 1;
}

#ifdef _WIN32
int timeout_lua_sleep(lua_State *L)
{
    double n = luaL_checknumber(L, 1);
    if (n < 0.0) n = 0.0;
    if (n < DBL_MAX/1000.0) n *= 1000.0;
    if (n > INT_MAX) n = INT_MAX;
    Sleep((int)n);
    return 0;
}
#else
int timeout_lua_sleep(lua_State *L)
{
    double n = luaL_checknumber(L, 1);
    struct timespec t, r;
    if (n < 0.0) n = 0.0;
    if (n > INT_MAX) n = INT_MAX;
    t.tv_sec = (int) n;
    n -= t.tv_sec;
    t.tv_nsec = (int) (n * 1000000000);
    if (t.tv_nsec >= 1000000000) t.tv_nsec = 999999999;
    while (nanosleep(&t, &r) != 0) {
        t.tv_sec = r.tv_sec;
        t.tv_nsec = r.tv_nsec;
    }
    return 0;
}
#endif
