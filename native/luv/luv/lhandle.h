#ifndef LUV_LHANDLE_H
#define LUV_LHANDLE_H

#include "luv.h"

#define luv_callback_id int
#define LUV_CLOSED 0
#define LUV_TIMEOUT 1
#define LUV_PREPARE 1
#define LUV_IDLE 1
#define LUV_CHECK 1
#define LUV_ASYNC 1
#define LUV_POLL 1
#define LUV_SIGNAL 1
#define LUV_EXIT 1
#define LUV_CONNECTION 1
#define LUV_READ 1
#define LUV_RECV 1
#define LUV_FS_EVENT 1
#define LUV_FS_POLL 1

typedef struct {
  int ref;
  int callbacks[2];
  void* extra;
} luv_handle_t;

static luv_handle_t* luv_setup_handle(lua_State* L);

static int luv_is_callable(lua_State* L, int index);

static void luv_check_callable(lua_State* L, int index);

static void luv_check_callback(lua_State* L, luv_handle_t* data, luv_callback_id id, int index);

static void luv_call_callback(lua_State* L, luv_handle_t* data, luv_callback_id id, int nargs);

static void luv_find_handle(lua_State* L, luv_handle_t* data);

static void luv_unref_handle(lua_State* L, luv_handle_t* data);

#endif
