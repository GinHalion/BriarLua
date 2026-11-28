#include <string.h>

#include "lua.h"
#include "lauxlib.h"

#if !defined(LUA_VERSION_NUM) || (LUA_VERSION_NUM < 501)
#include "compat-5.1.h"
#endif

#include "mime.h"

typedef unsigned char UC;
static const char CRLF[] = "\r\n";
static const char EQCRLF[] = "=\r\n";

static int mime_global_wrp(lua_State *L);
static int mime_global_b64(lua_State *L);
static int mime_global_unb64(lua_State *L);
static int mime_global_qp(lua_State *L);
static int mime_global_unqp(lua_State *L);
static int mime_global_qpwrp(lua_State *L);
static int mime_global_eol(lua_State *L);
static int mime_global_dot(lua_State *L);

static size_t dot(int c, size_t state, luaL_Buffer *buffer);
static void b64setup(UC *base);
static size_t b64encode(UC c, UC *input, size_t size, luaL_Buffer *buffer);
static size_t b64pad(const UC *input, size_t size, luaL_Buffer *buffer);
static size_t b64decode(UC c, UC *input, size_t size, luaL_Buffer *buffer);

static void qpsetup(UC *class, UC *unbase);
static void qpquote(UC c, luaL_Buffer *buffer);
static size_t qpdecode(UC c, UC *input, size_t size, luaL_Buffer *buffer);
static size_t qpencode(UC c, UC *input, size_t size,
        const char *marker, luaL_Buffer *buffer);
static size_t qppad(UC *input, size_t size, luaL_Buffer *buffer);

static luaL_Reg func[] = {
    { "dot", mime_global_dot },
    { "b64", mime_global_b64 },
    { "eol", mime_global_eol },
    { "qp", mime_global_qp },
    { "qpwrp", mime_global_qpwrp },
    { "unb64", mime_global_unb64 },
    { "unqp", mime_global_unqp },
    { "wrp", mime_global_wrp },
    { NULL, NULL }
};

static UC qpclass[256];
static UC qpbase[] = "0123456789ABCDEF";
static UC qpunbase[256];
enum {QP_PLAIN, QP_QUOTED, QP_CR, QP_IF_LAST};

static const UC b64base[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static UC b64unbase[256];

MIME_API int luaopen_mime_core(lua_State *L)
{
#if LUA_VERSION_NUM > 501 && !defined(LUA_COMPAT_MODULE)
    lua_newtable(L);
    luaL_setfuncs(L, func, 0);
#else
    luaL_openlib(L, "mime", func, 0);
#endif
    lua_pushstring(L, "_VERSION");
    lua_pushstring(L, MIME_VERSION);
    lua_rawset(L, -3);
    qpsetup(qpclass, qpunbase);
    b64setup(b64unbase);
    return 1;
}

static int mime_global_wrp(lua_State *L)
{
    size_t size = 0;
    int left = (int) luaL_checknumber(L, 1);
    const UC *input = (const UC *) luaL_optlstring(L, 2, NULL, &size);
    const UC *last = input + size;
    int length = (int) luaL_optnumber(L, 3, 76);
    luaL_Buffer buffer;
    if (!input) {
        if (left < length) lua_pushstring(L, CRLF);
        else lua_pushnil(L);
        lua_pushnumber(L, length);
        return 2;
    }
    luaL_buffinit(L, &buffer);
    while (input < last) {
        switch (*input) {
            case '\r':
                break;
            case '\n':
                luaL_addstring(&buffer, CRLF);
                left = length;
                break;
            default:
                if (left <= 0) {
                    left = length;
                    luaL_addstring(&buffer, CRLF);
                }
                luaL_addchar(&buffer, *input);
                left--;
                break;
        }
        input++;
    }
    luaL_pushresult(&buffer);
    lua_pushnumber(L, left);
    return 2;
}

static void b64setup(UC *unbase)
{
    int i;
    for (i = 0; i <= 255; i++) unbase[i] = (UC) 255;
    for (i = 0; i < 64; i++) unbase[b64base[i]] = (UC) i;
    unbase['='] = 0;
}

static size_t b64encode(UC c, UC *input, size_t size,
        luaL_Buffer *buffer)
{
    input[size++] = c;
    if (size == 3) {
        UC code[4];
        unsigned long value = 0;
        value += input[0]; value <<= 8;
        value += input[1]; value <<= 8;
        value += input[2];
        code[3] = b64base[value & 0x3f]; value >>= 6;
        code[2] = b64base[value & 0x3f]; value >>= 6;
        code[1] = b64base[value & 0x3f]; value >>= 6;
        code[0] = b64base[value];
        luaL_addlstring(buffer, (char *) code, 4);
        size = 0;
    }
    return size;
}

static size_t b64pad(const UC *input, size_t size,
        luaL_Buffer *buffer)
{
    unsigned long value = 0;
    UC code[4] = {'=', '=', '=', '='};
    switch (size) {
        case 1:
            value = input[0] << 4;
            code[1] = b64base[value & 0x3f]; value >>= 6;
            code[0] = b64base[value];
            luaL_addlstring(buffer, (char *) code, 4);
            break;
        case 2:
            value = input[0]; value <<= 8;
            value |= input[1]; value <<= 2;
            code[2] = b64base[value & 0x3f]; value >>= 6;
            code[1] = b64base[value & 0x3f]; value >>= 6;
            code[0] = b64base[value];
            luaL_addlstring(buffer, (char *) code, 4);
            break;
        default:
            break;
    }
    return 0;
}

static size_t b64decode(UC c, UC *input, size_t size,
        luaL_Buffer *buffer)
{
    if (b64unbase[c] > 64) return size;
    input[size++] = c;
    if (size == 4) {
        UC decoded[3];
        int valid, value = 0;
        value =  b64unbase[input[0]]; value <<= 6;
        value |= b64unbase[input[1]]; value <<= 6;
        value |= b64unbase[input[2]]; value <<= 6;
        value |= b64unbase[input[3]];
        decoded[2] = (UC) (value & 0xff); value >>= 8;
        decoded[1] = (UC) (value & 0xff); value >>= 8;
        decoded[0] = (UC) value;
        valid = (input[2] == '=') ? 1 : (input[3] == '=') ? 2 : 3;
        luaL_addlstring(buffer, (char *) decoded, valid);
        return 0;
    } else return size;
}

static int mime_global_b64(lua_State *L)
{
    UC atom[3];
    size_t isize = 0, asize = 0;
    const UC *input = (const UC *) luaL_optlstring(L, 1, NULL, &isize);
    const UC *last = input + isize;
    luaL_Buffer buffer;
    if (!input) {
        lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    lua_settop(L, 2);
    luaL_buffinit(L, &buffer);
    while (input < last)
        asize = b64encode(*input++, atom, asize, &buffer);
    input = (const UC *) luaL_optlstring(L, 2, NULL, &isize);
    if (!input) {
        size_t osize = 0;
        asize = b64pad(atom, asize, &buffer);
        luaL_pushresult(&buffer);
        lua_tolstring(L, -1, &osize);
        if (osize == 0) lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    last = input + isize;
    while (input < last)
        asize = b64encode(*input++, atom, asize, &buffer);
    luaL_pushresult(&buffer);
    lua_pushlstring(L, (char *) atom, asize);
    return 2;
}

static int mime_global_unb64(lua_State *L)
{
    UC atom[4];
    size_t isize = 0, asize = 0;
    const UC *input = (const UC *) luaL_optlstring(L, 1, NULL, &isize);
    const UC *last = input + isize;
    luaL_Buffer buffer;
    if (!input) {
        lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    lua_settop(L, 2);
    luaL_buffinit(L, &buffer);
    while (input < last)
        asize = b64decode(*input++, atom, asize, &buffer);
    input = (const UC *) luaL_optlstring(L, 2, NULL, &isize);
    if (!input) {
        size_t osize = 0;
        luaL_pushresult(&buffer);
        lua_tolstring(L, -1, &osize);
        if (osize == 0) lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    last = input + isize;
    while (input < last)
        asize = b64decode(*input++, atom, asize, &buffer);
    luaL_pushresult(&buffer);
    lua_pushlstring(L, (char *) atom, asize);
    return 2;
}

static void qpsetup(UC *cl, UC *unbase)
{
    int i;
    for (i = 0; i < 256; i++) cl[i] = QP_QUOTED;
    for (i = 33; i <= 60; i++) cl[i] = QP_PLAIN;
    for (i = 62; i <= 126; i++) cl[i] = QP_PLAIN;
    cl['\t'] = QP_IF_LAST;
    cl[' '] = QP_IF_LAST;
    cl['\r'] = QP_CR;
    for (i = 0; i < 256; i++) unbase[i] = 255;
    unbase['0'] = 0; unbase['1'] = 1; unbase['2'] = 2;
    unbase['3'] = 3; unbase['4'] = 4; unbase['5'] = 5;
    unbase['6'] = 6; unbase['7'] = 7; unbase['8'] = 8;
    unbase['9'] = 9; unbase['A'] = 10; unbase['a'] = 10;
    unbase['B'] = 11; unbase['b'] = 11; unbase['C'] = 12;
    unbase['c'] = 12; unbase['D'] = 13; unbase['d'] = 13;
    unbase['E'] = 14; unbase['e'] = 14; unbase['F'] = 15;
    unbase['f'] = 15;
}

static void qpquote(UC c, luaL_Buffer *buffer)
{
    luaL_addchar(buffer, '=');
    luaL_addchar(buffer, qpbase[c >> 4]);
    luaL_addchar(buffer, qpbase[c & 0x0F]);
}

static size_t qpencode(UC c, UC *input, size_t size,
        const char *marker, luaL_Buffer *buffer)
{
    input[size++] = c;
    while (size > 0) {
        switch (qpclass[input[0]]) {
            case QP_CR:
                if (size < 2) return size;
                if (input[1] == '\n') {
                    luaL_addstring(buffer, marker);
                    return 0;
                } else qpquote(input[0], buffer);
                break;
            case QP_IF_LAST:
                if (size < 3) return size;
                if (input[1] == '\r' && input[2] == '\n') {
                    qpquote(input[0], buffer);
                    luaL_addstring(buffer, marker);
                    return 0;
                } else luaL_addchar(buffer, input[0]);
                break;
            case QP_QUOTED:
                qpquote(input[0], buffer);
                break;
            default:
                luaL_addchar(buffer, input[0]);
                break;
        }
        input[0] = input[1]; input[1] = input[2];
        size--;
    }
    return 0;
}

static size_t qppad(UC *input, size_t size, luaL_Buffer *buffer)
{
    size_t i;
    for (i = 0; i < size; i++) {
        if (qpclass[input[i]] == QP_PLAIN) luaL_addchar(buffer, input[i]);
        else qpquote(input[i], buffer);
    }
    if (size > 0) luaL_addstring(buffer, EQCRLF);
    return 0;
}

static int mime_global_qp(lua_State *L)
{

    size_t asize = 0, isize = 0;
    UC atom[3];
    const UC *input = (const UC *) luaL_optlstring(L, 1, NULL, &isize);
    const UC *last = input + isize;
    const char *marker = luaL_optstring(L, 3, CRLF);
    luaL_Buffer buffer;
    if (!input) {
        lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    lua_settop(L, 3);
    luaL_buffinit(L, &buffer);
    while (input < last)
        asize = qpencode(*input++, atom, asize, marker, &buffer);
    input = (const UC *) luaL_optlstring(L, 2, NULL, &isize);
    if (!input) {
        asize = qppad(atom, asize, &buffer);
        luaL_pushresult(&buffer);
        if (!(*lua_tostring(L, -1))) lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    last = input + isize;
    while (input < last)
        asize = qpencode(*input++, atom, asize, marker, &buffer);
    luaL_pushresult(&buffer);
    lua_pushlstring(L, (char *) atom, asize);
    return 2;
}

static size_t qpdecode(UC c, UC *input, size_t size, luaL_Buffer *buffer) {
    int d;
    input[size++] = c;
    switch (input[0]) {
        case '=':
            if (size < 3) return size;
            if (input[1] == '\r' && input[2] == '\n') return 0;
            c = qpunbase[input[1]]; d = qpunbase[input[2]];
            if (c > 15 || d > 15) luaL_addlstring(buffer, (char *)input, 3);
            else luaL_addchar(buffer, (char) ((c << 4) + d));
            return 0;
        case '\r':
            if (size < 2) return size;
            if (input[1] == '\n') luaL_addlstring(buffer, (char *)input, 2);
            return 0;
        default:
            if (input[0] == '\t' || (input[0] > 31 && input[0] < 127))
                luaL_addchar(buffer, input[0]);
            return 0;
    }
}

static int mime_global_unqp(lua_State *L)
{
    size_t asize = 0, isize = 0;
    UC atom[3];
    const UC *input = (const UC *) luaL_optlstring(L, 1, NULL, &isize);
    const UC *last = input + isize;
    luaL_Buffer buffer;
    if (!input) {
        lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    lua_settop(L, 2);
    luaL_buffinit(L, &buffer);
    while (input < last)
        asize = qpdecode(*input++, atom, asize, &buffer);
    input = (const UC *) luaL_optlstring(L, 2, NULL, &isize);
    if (!input) {
        luaL_pushresult(&buffer);
        if (!(*lua_tostring(L, -1))) lua_pushnil(L);
        lua_pushnil(L);
        return 2;
    }
    last = input + isize;
    while (input < last)
        asize = qpdecode(*input++, atom, asize, &buffer);
    luaL_pushresult(&buffer);
    lua_pushlstring(L, (char *) atom, asize);
    return 2;
}

static int mime_global_qpwrp(lua_State *L)
{
    size_t size = 0;
    int left = (int) luaL_checknumber(L, 1);
    const UC *input = (const UC *) luaL_optlstring(L, 2, NULL, &size);
    const UC *last = input + size;
    int length = (int) luaL_optnumber(L, 3, 76);
    luaL_Buffer buffer;
    if (!input) {
        if (left < length) lua_pushstring(L, EQCRLF);
        else lua_pushnil(L);
        lua_pushnumber(L, length);
        return 2;
    }
    luaL_buffinit(L, &buffer);
    while (input < last) {
        switch (*input) {
            case '\r':
                break;
            case '\n':
                left = length;
                luaL_addstring(&buffer, CRLF);
                break;
            case '=':
                if (left <= 3) {
                    left = length;
                    luaL_addstring(&buffer, EQCRLF);
                }
                luaL_addchar(&buffer, *input);
                left--;
                break;
            default:
                if (left <= 1) {
                    left = length;
                    luaL_addstring(&buffer, EQCRLF);
                }
                luaL_addchar(&buffer, *input);
                left--;
                break;
        }
        input++;
    }
    luaL_pushresult(&buffer);
    lua_pushnumber(L, left);
    return 2;
}

#define eolcandidate(c) (c == '\r' || c == '\n')
static int eolprocess(int c, int last, const char *marker,
        luaL_Buffer *buffer)
{
    if (eolcandidate(c)) {
        if (eolcandidate(last)) {
            if (c == last) luaL_addstring(buffer, marker);
            return 0;
        } else {
            luaL_addstring(buffer, marker);
            return c;
        }
    } else {
        luaL_addchar(buffer, (char) c);
        return 0;
    }
}

static int mime_global_eol(lua_State *L)
{
    int ctx = luaL_checkinteger(L, 1);
    size_t isize = 0;
    const char *input = luaL_optlstring(L, 2, NULL, &isize);
    const char *last = input + isize;
    const char *marker = luaL_optstring(L, 3, CRLF);
    luaL_Buffer buffer;
    luaL_buffinit(L, &buffer);
    if (!input) {
       lua_pushnil(L);
       lua_pushnumber(L, 0);
       return 2;
    }
    while (input < last)
        ctx = eolprocess(*input++, ctx, marker, &buffer);
    luaL_pushresult(&buffer);
    lua_pushnumber(L, ctx);
    return 2;
}

static size_t dot(int c, size_t state, luaL_Buffer *buffer)
{
    luaL_addchar(buffer, (char) c);
    switch (c) {
        case '\r':
            return 1;
        case '\n':
            return (state == 1)? 2: 0;
        case '.':
            if (state == 2)
                luaL_addchar(buffer, '.');
        default:
            return 0;
    }
}

static int mime_global_dot(lua_State *L)
{
    size_t isize = 0, state = (size_t) luaL_checknumber(L, 1);
    const char *input = luaL_optlstring(L, 2, NULL, &isize);
    const char *last = input + isize;
    luaL_Buffer buffer;
    if (!input) {
        lua_pushnil(L);
        lua_pushnumber(L, 2);
        return 2;
    }
    luaL_buffinit(L, &buffer);
    while (input < last)
        state = dot(*input++, state, &buffer);
    luaL_pushresult(&buffer);
    lua_pushnumber(L, (lua_Number) state);
    return 2;
}

