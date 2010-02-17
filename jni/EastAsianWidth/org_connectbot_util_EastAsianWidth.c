/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* vim: set ts=4 sw=4 expandtab cindent: */
#define LOG_TAG "ConnectBot.util.EastAsianWidth"

#include <jni.h>
#include <android/log.h>
#include <utypes.h>
#include <uchar.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG, __VA_ARGS__)

/**
 * org.connectbot.util.EastAsianWidth
 */

Java_org_connectbot_util_EastAsianWidth_measure(JNIEnv *env, jobject thiz,
        jcharArray buffer, jint start, jint len, jbyteArray attributes, jboolean eastAsian) {

    UErrorCode errorCode = U_ZERO_ERROR;
    jint end = start + len;
    jint i = start;
    UChar32 c;

    const jchar *uBuffer = (jchar *) (*env)->GetPrimitiveArrayCritical(env, buffer, (jboolean *)0);
    if (!uBuffer) {
        LOGE("Could not obtain source buffer");
        errorCode = U_ILLEGAL_ARGUMENT_ERROR;
        goto exit;
    }

    jbyte *uAttributes = (jbyte *) (*env)->GetPrimitiveArrayCritical(env, attributes, (jboolean *)0);
    if (!uAttributes) {
        LOGE("Could not obtain attribute array");
        errorCode = U_ILLEGAL_ARGUMENT_ERROR;
        goto exit;
    }

    while (i < end) {
        U16_NEXT(uBuffer, i, end, c);
        UEastAsianWidth ea = (UEastAsianWidth) u_getIntPropertyValue(c, UCHAR_EAST_ASIAN_WIDTH);

        switch (ea) {
            case U_EA_FULLWIDTH:
            case U_EA_WIDE:
                uAttributes[i] = 1;
                break;
            case U_EA_HALFWIDTH:
            case U_EA_NARROW:
                uAttributes[i] = 0;
                break;
            case U_EA_NEUTRAL:
            case U_EA_AMBIGUOUS:
            default:
                if (eastAsian)
                    uAttributes[i] = 1;
                else
                    uAttributes[i] = 0;
                break;
        }
    }

exit:
    if (uBuffer)
        (*env)->ReleasePrimitiveArrayCritical(env, buffer, (jchar *)uBuffer, JNI_ABORT);
    if (uAttributes)
        (*env)->ReleasePrimitiveArrayCritical(env, attributes, uAttributes, JNI_ABORT);

    return errorCode;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    jint result = -1;
    jclass clazz;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed\n");
        goto bail;
    }
    
    result = JNI_VERSION_1_4;

bail:
    return result;
}
