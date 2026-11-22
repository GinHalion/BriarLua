#ifndef LUV_LREQ_H
#define LUV_LREQ_H

#include "luv.h"

typedef struct {
  int req_ref;
  int callback_ref;
  int data_ref;
  void* data;
} luv_req_t;

static int luv_check_continuation(lua_State* L, int index);

static luv_req_t* luv_setup_req(lua_State* L, int ref);

static void luv_fulfill_req(lua_State* L, luv_req_t* data, int nargs);

static void luv_cleanup_req(lua_State* L, luv_req_t* data);

#endif
