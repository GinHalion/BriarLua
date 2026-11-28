LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := lua
LOCAL_CFLAGS += -std=c99


LOCAL_SRC_FILES := \
	lapi.c \
	lauxlib.c \
	lbaselib.c \
	lbitlib.c \
	lcode.c \
	lcorolib.c \
	lctype.c \
	ldblib.c \
	ldebug.c \
	ldo.c \
	ldump.c \
	lfunc.c \
	lgc.c \
	linit.c \
	liolib.c \
	llex.c \
	lmathlib.c \
	lmem.c \
	loadlib.c \
	lobject.c \
	lopcodes.c \
	loslib.c \
	lparser.c \
	lstate.c \
	lstring.c \
	lstrlib.c \
	ltable.c \
	ltablib.c \
	ltm.c \
	lua.c \
	luac.c \
	lundump.c \
	lutf8lib.c \
	lvm.c \
	lzio.c 


LOCAL_CFLAGS += -DLUA_DL_DLOPEN -D_DEFAULT_SOURCE -D_POSIX_C_SOURCE=200809L
LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog -ldl
include $(BUILD_STATIC_LIBRARY) 
