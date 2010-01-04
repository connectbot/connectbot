LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)


LOCAL_SRC_FILES := org_connectbot_util_EastAsianWidth.c
LOCAL_MODULE := org_connectbot_util_EastAsianWidth
LOCAL_CFLAGS += -I$(LOCAL_PATH)/unicode

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -licuuc -llog

include $(BUILD_SHARED_LIBRARY)
