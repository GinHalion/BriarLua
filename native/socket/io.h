#ifndef IO_H
#define IO_H
#include <stdio.h>
#include "lua.h"

#include "timeout.h"

enum {
    IO_DONE = 0,
    IO_TIMEOUT = -1,
    IO_CLOSED = -2,
	IO_UNKNOWN = -3
};

typedef const char *(*p_error) (
    void *ctx,
    int err
);

typedef int (*p_send) (
    void *ctx,
    const char *data,
    size_t count,
    size_t *sent,
    p_timeout tm
);

typedef int (*p_recv) (
    void *ctx,
    char *data,
    size_t count,
    size_t *got,
    p_timeout tm
);

typedef struct t_io_ {
    void *ctx;
    p_send send;
    p_recv recv;
    p_error error;
} t_io;
typedef t_io *p_io;

void io_init(p_io io, p_send send, p_recv recv, p_error error, void *ctx);
const char *io_strerror(int err);

#endif

