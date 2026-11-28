#ifndef BUF_H
#define BUF_H
#include "lua.h"

#include "io.h"
#include "timeout.h"

#define BUF_SIZE 8192

typedef struct t_buffer_ {
    double birthday;
    size_t sent, received;
    p_io io;
    p_timeout tm;
    size_t first, last;
    char data[BUF_SIZE];
} t_buffer;
typedef t_buffer *p_buffer;

int buffer_open(lua_State *L);
void buffer_init(p_buffer buf, p_io io, p_timeout tm);
int buffer_meth_send(lua_State *L, p_buffer buf);
int buffer_meth_receive(lua_State *L, p_buffer buf);
int buffer_meth_getstats(lua_State *L, p_buffer buf);
int buffer_meth_setstats(lua_State *L, p_buffer buf);
int buffer_isempty(p_buffer buf);

#endif
