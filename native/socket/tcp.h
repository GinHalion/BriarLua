#ifndef TCP_H
#define TCP_H
#include "lua.h"

#include "buffer.h"
#include "timeout.h"
#include "socket.h"

typedef struct t_tcp_ {
    t_socket sock;
    t_io io;
    t_buffer buf;
    t_timeout tm;
    int family;
} t_tcp;

typedef t_tcp *p_tcp;

int tcp_open(lua_State *L);

#endif
