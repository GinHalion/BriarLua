#ifndef UDP_H
#define UDP_H
#include "lua.h"

#include "timeout.h"
#include "socket.h"

#define UDP_DATAGRAMSIZE 8192

typedef struct t_udp_ {
    t_socket sock;
    t_timeout tm;
    int family;
} t_udp;
typedef t_udp *p_udp;

int udp_open(lua_State *L);

#endif
