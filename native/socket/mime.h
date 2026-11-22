#ifndef MIME_H
#define MIME_H
#include "lua.h"

#define MIME_VERSION    "MIME 1.0.3"
#define MIME_COPYRIGHT  "Copyright (C) 2004-2013 Diego Nehab"
#define MIME_AUTHORS    "Diego Nehab"

#ifndef MIME_API
#define MIME_API extern
#endif

MIME_API int luaopen_mime_core(lua_State *L);

#endif
