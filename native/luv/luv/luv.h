#ifndef LUV_H
#define LUV_H
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
#include "uv.h"

#include <string.h>
#include <stdlib.h>
#include <assert.h>

#if defined(_WIN32)
# include <fcntl.h>
# include <sys/types.h>
# include <sys/stat.h>
# ifndef __MINGW32__
#   define S_ISREG(x)  (((x) & _S_IFMT) == _S_IFREG)
#   define S_ISDIR(x)  (((x) & _S_IFMT) == _S_IFDIR)
#   define S_ISFIFO(x) (((x) & _S_IFMT) == _S_IFIFO)
#   define S_ISCHR(x)  (((x) & _S_IFMT) == _S_IFCHR)
#   define S_ISBLK(x)  0
# endif
# define S_ISLNK(x)  (((x) & S_IFLNK) == S_IFLNK)
# define S_ISSOCK(x) 0
#else
# include <unistd.h>
#endif

#ifndef PATH_MAX
#define PATH_MAX (8096)
#endif

#ifndef MAX_TITLE_LENGTH
#define MAX_TITLE_LENGTH (8192)
#endif

#if (LUA_VERSION_NUM != 503)
#include "c-api/compat-5.3.h"
#endif

LUALIB_API lua_State* luv_state(uv_loop_t* loop);
LUALIB_API uv_loop_t* luv_loop(lua_State* L);

LUALIB_API int luaopen_luv (lua_State *L);

#include "util.h"
#include "lhandle.h"
#include "lreq.h"

static uv_stream_t* luv_check_stream(lua_State* L, int index);
static void luv_alloc_cb(uv_handle_t* handle, size_t suggested_size, uv_buf_t* buf);
static void luv_check_buf(lua_State *L, int idx, uv_buf_t *pbuf);
static uv_buf_t* luv_prep_bufs(lua_State* L, int index, size_t *count);

static void parse_sockaddr(lua_State* L, struct sockaddr_storage* address);
static void luv_connect_cb(uv_connect_t* req, int status);

static void luv_push_stats_table(lua_State* L, const uv_stat_t* s);

static int luv_af_string_to_num(const char* string);
static const char* luv_af_num_to_string(const int num);
static int luv_sock_string_to_num(const char* string);
static const char* luv_sock_num_to_string(const int num);
static int luv_sig_string_to_num(const char* string);
static const char* luv_sig_num_to_string(const int num);

typedef lua_State* (*luv_acquire_vm)();
typedef void (*luv_release_vm)(lua_State* L);
LUALIB_API void luv_set_thread_cb(luv_acquire_vm acquire, luv_release_vm release);

#endif
