LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := com_google_ase_Exec
LOCAL_CFLAGS    := -Werror
LOCAL_SRC_FILES := com_google_ase_Exec.cpp
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)
