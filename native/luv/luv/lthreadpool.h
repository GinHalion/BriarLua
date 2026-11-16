#ifndef LUV_LTHREADPOOL_H
#define LUV_LTHREADPOOL_H

#include "luv.h"

#define LUV_THREAD_MAXNUM_ARG 9

typedef struct {
  int type;
  union
  {
    lua_Number num;
    int boolean;
    void* userdata;
    struct {
      const char* base;
      size_t len;
    } str;
  } val;
} luv_val_t;

typedef struct {
  int argc;
  luv_val_t argv[LUV_THREAD_MAXNUM_ARG];
} luv_thread_arg_t;

#ifndef LUA_OK
#define LUA_OK 0
#endif

#define LUVF_THREAD_UHANDLE 1

static int luv_thread_arg_set(lua_State* L, luv_thread_arg_t* args, int idx, int top, int flags);
static int luv_thread_arg_push(lua_State* L, const luv_thread_arg_t* args, int flags);
static void luv_thread_arg_clear(lua_State* L, luv_thread_arg_t* args, int flags);

#endif
