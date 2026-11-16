

#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <lua.h>
#include <lauxlib.h>

#include "md5.h"
#include "compat-5.2.h"


static int lmd5 (lua_State *L) {
  char buff[16];
  size_t l;
  const char *message = luaL_checklstring(L, 1, &l);
  md5(message, l, buff);
  lua_pushlstring(L, buff, 16L);
  return 1;
}


static int ex_or (lua_State *L) {
  size_t l1, l2;
  const char *s1 = luaL_checklstring(L, 1, &l1);
  const char *s2 = luaL_checklstring(L, 2, &l2);
  luaL_Buffer b;
  luaL_argcheck( L, l1 == l2, 2, "lengths must be equal" );
  luaL_buffinit(L, &b);
  while (l1--) luaL_addchar(&b, (*s1++)^(*s2++));
  luaL_pushresult(&b);
  return 1;
}


static void checkseed (lua_State *L) {
  if (lua_isnone(L, 3)) {
    time_t tm = time(NULL);
    lua_pushlstring(L, (char *)&tm, sizeof(tm));
  }
}


#define MAXKEY	256
#define BLOCKSIZE	16



static int initblock (lua_State *L, const char *seed, int lseed, char *block) {
  size_t lkey;
  const char *key = luaL_checklstring(L, 2, &lkey);
  if (lkey > MAXKEY)
    luaL_error(L, "key too long (> %d)", MAXKEY);
  memset(block, 0, BLOCKSIZE);
  memcpy(block, seed, lseed);
  memcpy(block+BLOCKSIZE, key, lkey);
  return (int)lkey+BLOCKSIZE;
}


static void codestream (lua_State *L, const char *msg, size_t lmsg,
                                      char *block, int lblock) {
  luaL_Buffer b;
  luaL_buffinit(L, &b);
  while (lmsg > 0) {
    char code[BLOCKSIZE];
    int i;
    md5(block, lblock, code);
    for (i=0; i<BLOCKSIZE && lmsg > 0; i++, lmsg--)
      code[i] ^= *msg++;
    luaL_addlstring(&b, code, i);
    memcpy(block, code, i);
  }
  luaL_pushresult(&b);
}


static void decodestream (lua_State *L, const char *cypher, size_t lcypher,
                          char *block, int lblock) {
  luaL_Buffer b;
  luaL_buffinit(L, &b);
  while (lcypher > 0) {
    char code[BLOCKSIZE];
    int i;
    md5(block, lblock, code);
    for (i=0; i<BLOCKSIZE && lcypher > 0; i++, lcypher--)
      code[i] ^= *cypher++;
    luaL_addlstring(&b, code, i);
    memcpy(block, cypher-i, i);
  }
  luaL_pushresult(&b);
}


static int crypt (lua_State *L) {
  size_t lmsg;
  const char *msg = luaL_checklstring(L, 1, &lmsg);
  size_t lseed;
  const char *seed;
  int lblock;
  char block[BLOCKSIZE+MAXKEY];
  checkseed(L);
  seed = luaL_checklstring(L, 3, &lseed);
  if (lseed > BLOCKSIZE)
    luaL_error(L, "seed too long (> %d)", BLOCKSIZE);
  block[0] = (char)lseed;
  memcpy(block+1, seed, lseed);
  lua_pushlstring(L, block, lseed+1);
  lblock = initblock(L, seed, lseed, block);
  codestream(L, msg, lmsg, block, lblock);
  lua_concat(L, 2);
  return 1;
}


static int decrypt (lua_State *L) {
  size_t lcyphertext;
  const char *cyphertext = luaL_checklstring(L, 1, &lcyphertext);
  size_t lseed = cyphertext[0];
  const char *seed = cyphertext+1;
  int lblock;
  char block[BLOCKSIZE+MAXKEY];
  luaL_argcheck(L, lcyphertext >= lseed+1 && lseed <= BLOCKSIZE, 1,
                 "invalid cyphered string");
  cyphertext += lseed+1;
  lcyphertext -= lseed+1;
  lblock = initblock(L, seed, lseed, block);
  decodestream(L, cyphertext, lcyphertext, block, lblock);
  return 1;
}


static void set_info (lua_State *L) {
	lua_pushliteral (L, "_COPYRIGHT");
	lua_pushliteral (L, "Copyright (C) 2003-2013 PUC-Rio");
	lua_settable (L, -3);
	lua_pushliteral (L, "_DESCRIPTION");
	lua_pushliteral (L, "Basic cryptographic facilities");
	lua_settable (L, -3);
	lua_pushliteral (L, "_VERSION");
	lua_pushliteral (L, "MD5 1.2");
	lua_settable (L, -3);
}


static struct luaL_Reg md5lib[] = {
  {"sum", lmd5},
  {"exor", ex_or},
  {"crypt", crypt},
  {"decrypt", decrypt},
  {NULL, NULL}
};


int luaopen_md5 (lua_State *L) {
  luaL_newlib(L,md5lib);
  set_info (L);
  return 1;
}
