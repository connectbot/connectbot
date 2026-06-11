/*
 * Mosh support for ConnectBot
 * Copyright 2012 Daniel Drown (transport layer)
 * ConnectBot integration by bqv (Tony O)
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

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#include "android/log.h"

#define LOG_TAG "MoshClient"
#define LOG(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static thread_local char g_last_error[512];

static void set_last_error(const char* format, ...) {
  va_list args;
  va_start(args, format);
  vsnprintf(g_last_error, sizeof(g_last_error), format, args);
  va_end(args);
  LOG("%s", g_last_error);
}

static void JNU_ThrowByName(JNIEnv* env, const char* name, const char* msg) {
  jclass clazz = env->FindClass(name);
  if (clazz != NULL) {
    env->ThrowNew(clazz, msg);
  }
  env->DeleteLocalRef(clazz);
}

static char* JNU_GetStringNativeChars(JNIEnv* env, jstring jstr) {
  if (jstr == NULL) {
    return NULL;
  }
  jbyteArray bytes = 0;
  jthrowable exc;
  char* result = 0;
  if (env->EnsureLocalCapacity(2) < 0) {
    return 0; /* out of memory error */
  }
  jclass Class_java_lang_String = env->FindClass("java/lang/String");
  jmethodID MID_String_getBytes = env->GetMethodID(
      Class_java_lang_String, "getBytes", "()[B");
  bytes = (jbyteArray) env->CallObjectMethod(jstr, MID_String_getBytes);
  exc = env->ExceptionOccurred();
  if (!exc) {
    jint len = env->GetArrayLength(bytes);
    result = (char*) malloc(len + 1);
    if (result == 0) {
      JNU_ThrowByName(env, "java/lang/OutOfMemoryError", 0);
      env->DeleteLocalRef(bytes);
      return 0;
    }
    env->GetByteArrayRegion(bytes, 0, len, (jbyte*) result);
    result[len] = 0; /* NULL-terminate */
  } else {
    env->DeleteLocalRef(exc);
  }
  env->DeleteLocalRef(bytes);
  return result;
}

static int jniGetFDFromFileDescriptor(JNIEnv* env, jobject fileDescriptor) {
  jclass Class_java_io_FileDescriptor = env->FindClass("java/io/FileDescriptor");
  jfieldID descriptor = env->GetFieldID(Class_java_io_FileDescriptor,
                                        "descriptor", "I");
  return env->GetIntField(fileDescriptor, descriptor);
}

static int create_mosh_subprocess(
    const char* client_path, const char* ip, const char* port, const char* key,
    const char* terminfo, const char* locale, int row, int col, int xpixel,
    int ypixel, long* pProcessId) {
  char devname[32];
  int exec_error_pipe[2];
  int ptm;
  pid_t pid;

  if (client_path == NULL) {
    set_last_error("mosh-client executable path is null");
    return -1;
  }
  if (access(client_path, X_OK) != 0) {
    set_last_error("mosh-client is not executable: %s - %s", client_path, strerror(errno));
    return -1;
  }

  ptm = open("/dev/ptmx", O_RDWR);
  if (ptm < 0) {
    set_last_error("cannot open /dev/ptmx - %s", strerror(errno));
    return -1;
  }
  fcntl(ptm, F_SETFD, FD_CLOEXEC);

  if (
#if !defined(ANDROID)
      /* this actually doesn't do anything on Android */
      grantpt(ptm) ||
#endif
      unlockpt(ptm) ||
      ptsname_r(ptm, devname, sizeof(devname))) {
    set_last_error("trouble with /dev/ptmx - %s", strerror(errno));
    close(ptm);
    return -1;
  }

  if (pipe(exec_error_pipe) != 0) {
    set_last_error("pipe failed: %s", strerror(errno));
    close(ptm);
    return -1;
  }
  fcntl(exec_error_pipe[1], F_SETFD, FD_CLOEXEC);

  pid = fork();
  if (pid < 0) {
    set_last_error("fork failed: %s", strerror(errno));
    close(exec_error_pipe[0]);
    close(exec_error_pipe[1]);
    close(ptm);
    return -1;
  }

  if (pid == 0) {
    int pts;
    int exec_errno;

    close(exec_error_pipe[0]);
    setsid();

    pts = open(devname, O_RDWR);
    if (pts < 0) {
      exec_errno = errno;
      write(exec_error_pipe[1], &exec_errno, sizeof(exec_errno));
      _exit(127);
    }

    dup2(pts, 0);
    dup2(pts, 1);
    dup2(pts, 2);

    struct winsize sz;
    sz.ws_row = row > 0 ? row : 24;
    sz.ws_col = col > 0 ? col : 80;
    sz.ws_xpixel = xpixel > 0 ? xpixel : 0;
    sz.ws_ypixel = ypixel > 0 ? ypixel : 0;
    ioctl(pts, TIOCSWINSZ, &sz);

    close(ptm);

    // Set environment variables for mosh-client
    setenv("MOSH_KEY", key, 1);
    setenv("TERMINFO", terminfo, 1);
    setenv("LC_ALL", locale, 1);
    setenv("LANG", locale, 1);
    setenv("TERM", "xterm-256color", 1);

    // Execute mosh-client
    // mosh-client expects: mosh-client <ip> <port>
    execl(client_path, "mosh-client", ip, port, NULL);

    // Android 10+ denies direct execve() from writable app data. Running the
    // platform linker directly still lets it map the downloaded executable.
    exec_errno = errno;
    if (exec_errno == EACCES) {
      const char* linker_path = sizeof(void*) == 8 ? "/system/bin/linker64" : "/system/bin/linker";
      LOG("direct mosh-client exec failed with EACCES; retrying via %s", linker_path);
      execl(linker_path, linker_path, client_path, ip, port, NULL);
      exec_errno = errno;
    }

    // If execl fails, report errno to the parent and exit.
    write(exec_error_pipe[1], &exec_errno, sizeof(exec_errno));
    LOG("[ execl mosh-client failed: %s ]\n", strerror(exec_errno));
    _exit(127);
  } else {
    int exec_errno = 0;
    ssize_t bytes_read;

    close(exec_error_pipe[1]);
    do {
      bytes_read = read(exec_error_pipe[0], &exec_errno, sizeof(exec_errno));
    } while (bytes_read < 0 && errno == EINTR);
    close(exec_error_pipe[0]);

    if (bytes_read == sizeof(exec_errno)) {
      set_last_error("mosh-client exec failed: %s", strerror(exec_errno));
      waitpid(pid, NULL, 0);
      close(ptm);
      return -1;
    } else if (bytes_read < 0) {
      set_last_error("mosh-client exec status read failed: %s", strerror(errno));
      waitpid(pid, NULL, 0);
      close(ptm);
      return -1;
    }

    *pProcessId = (long) pid;
    return ptm;
  }
}

extern "C" {

JNIEXPORT jobject JNICALL Java_org_mosh_MoshClient_forkExec(
    JNIEnv* env, jclass clazz, jstring clientPath, jstring ip, jstring port,
    jstring key, jstring terminfo, jstring locale, jint row, jint col,
    jint xpixel, jint ypixel, jlongArray processIdArray) {

  g_last_error[0] = '\0';

  char* client_path_8 = JNU_GetStringNativeChars(env, clientPath);
  char* ip_8 = JNU_GetStringNativeChars(env, ip);
  char* port_8 = JNU_GetStringNativeChars(env, port);
  char* key_8 = JNU_GetStringNativeChars(env, key);
  char* terminfo_8 = JNU_GetStringNativeChars(env, terminfo);
  char* locale_8 = JNU_GetStringNativeChars(env, locale);

  long procId;
  int ptm = create_mosh_subprocess(
      client_path_8, ip_8, port_8, key_8, terminfo_8, locale_8, row, col,
      xpixel, ypixel, &procId);

  free(client_path_8);
  free(ip_8);
  free(port_8);
  free(key_8);
  free(terminfo_8);
  free(locale_8);

  if (ptm < 0) {
    JNU_ThrowByName(
        env,
        "java/io/IOException",
        g_last_error[0] != '\0' ? g_last_error : "failed to fork or exec mosh-client");
    return NULL;
  }

  if (processIdArray) {
    int procIdLen = env->GetArrayLength(processIdArray);
    if (procIdLen > 0) {
      jboolean isCopy;
      jlong* pProcId = (jlong*) env->GetPrimitiveArrayCritical(processIdArray, &isCopy);
      if (pProcId) {
        *pProcId = procId;
        env->ReleasePrimitiveArrayCritical(processIdArray, pProcId, 0);
      }
    }
  }

  jclass Class_java_io_FileDescriptor = env->FindClass("java/io/FileDescriptor");
  jmethodID init = env->GetMethodID(Class_java_io_FileDescriptor,
                                    "<init>", "()V");
  jobject result = env->NewObject(Class_java_io_FileDescriptor, init);

  if (!result) {
    LOG("Couldn't create a FileDescriptor.");
    close(ptm);
  } else {
    jfieldID descriptor = env->GetFieldID(Class_java_io_FileDescriptor,
                                          "descriptor", "I");
    env->SetIntField(result, descriptor, ptm);
  }

  return result;
}

JNIEXPORT void JNICALL Java_org_mosh_MoshClient_setPtyWindowSize(
    JNIEnv* env, jclass clazz, jobject fileDescriptor, jint row, jint col,
    jint xpixel, jint ypixel) {
  int fd;
  struct winsize sz;

  fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

  if (env->ExceptionOccurred() != NULL) {
    return;
  }

  sz.ws_row = row;
  sz.ws_col = col;
  sz.ws_xpixel = xpixel;
  sz.ws_ypixel = ypixel;

  ioctl(fd, TIOCSWINSZ, &sz);
}

JNIEXPORT jint JNICALL Java_org_mosh_MoshClient_setenv(
    JNIEnv* env, jclass clazz, jstring name, jstring value) {
  char* name_8 = JNU_GetStringNativeChars(env, name);
  char* value_8 = JNU_GetStringNativeChars(env, value);

  int result = setenv(name_8, value_8, 1);

  free(name_8);
  free(value_8);

  return result;
}

JNIEXPORT jint JNICALL Java_org_mosh_MoshClient_waitFor(
    JNIEnv* env, jclass clazz, jlong procId) {
  int status;
  waitpid((pid_t) procId, &status, 0);
  int result = 0;
  if (WIFEXITED(status)) {
    result = WEXITSTATUS(status);
  }
  return result;
}

JNIEXPORT jint JNICALL Java_org_mosh_MoshClient_kill(
    JNIEnv* env, jclass clazz, jlong pid, jint signal) {
  return kill((pid_t) pid, signal);
}

} // extern "C"
