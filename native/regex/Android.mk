LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := regex
LOCAL_SRC_FILES  := lposix.c common.c
LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog -ldl -lz
LOCAL_STATIC_LIBRARIES := lua

include $(BUILD_SHARED_LIBRARY)
