#include "jni.h"
#include "android/log.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <memory>
#include <thread>
#include <sys/prctl.h>
#include <iostream>

#define LOG_TAG "MoshClient"
#define LOG_F(...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)
#define LOG_E(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOG_W(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOG_I(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOG_D(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOG_V(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

void jniThrowByName(JNIEnv* env, const char* name, const char* msg) {
  jclass clazz = env->FindClass(name);
  if (clazz != NULL) {
    env->ThrowNew(clazz, msg);
  }
  env->DeleteLocalRef(clazz);
}

std::unique_ptr<char[]> jniGetStringNativeChars(JNIEnv* env, jstring jstr) {
  if (jstr == NULL) {
    return nullptr;
  }

  const char* nativeString = env->GetStringUTFChars(jstr, NULL);
  if (nativeString == NULL) {
    env->ExceptionClear();
    return nullptr; /* out of memory error */
  }

  size_t len = strlen(nativeString);
  std::unique_ptr<char[]> result(new(std::nothrow) char[len + 1]);
  if (result != NULL) {
    memcpy(result.get(), nativeString, len + 1);
  } else {
    jniThrowByName(env, "java/lang/OutOfMemoryError",
                   "Failed to allocate memory for native string");
  }

  env->ReleaseStringUTFChars(jstr, nativeString);

  return result;
}

int jniGetFDFromFileDescriptor(JNIEnv* env, jobject fileDescriptor) {
  jclass Class_java_io_FileDescriptor = env->FindClass("java/io/FileDescriptor");
  jfieldID descriptor = env->GetFieldID(Class_java_io_FileDescriptor,
                                        "descriptor", "I");
  return env->GetIntField(fileDescriptor, descriptor);
}

extern "C" {
  int mosh_client_main(int argc, char* argv[]);

  __attribute__((visibility("default")))
  JNIEXPORT jint Java_org_mosh_MoshClient_main(JNIEnv* env, jclass clazz,
                                               jobjectArray args) {
    // Convert jobjectArray to char**
    int argc = env->GetArrayLength(args);
    char **argv = (char **)malloc(argc * sizeof(char *));
    for (int i = 0; i < argc; i++) {
      jstring str = (jstring)env->GetObjectArrayElement(args, i);
      const char *cstr = env->GetStringUTFChars(str, 0);
      argv[i] = strdup(cstr);
      env->ReleaseStringUTFChars(str, cstr);
    }

    // Call the original main function from mosh-client.o
    int result = mosh_client_main(argc, argv);

    // Free args
    for (int i = 0; i < argc; i++) {
      free(argv[i]);
    }
    free(argv);

    return result;
  }

  JNIEXPORT jint JNICALL Java_org_mosh_MoshClient_setenv(JNIEnv* env, jclass clazz,
                                                         jstring name, jstring value) {
    std::unique_ptr<char[]> name_8 = jniGetStringNativeChars(env, name);
    std::unique_ptr<char[]> value_8 = jniGetStringNativeChars(env, value);

    return setenv(name_8.get(), value_8.get(), 1);
  }

  JNIEXPORT void Java_org_mosh_MoshClient_setPtyWindowSize(JNIEnv* env, jclass clazz,
                                                           jobject fileDescriptor,
                                                           jint row, jint col,
                                                           jint xpixel, jint ypixel) {
    int fd;
    struct winsize sz;

    fd = jniGetFDFromFileDescriptor(env, fileDescriptor);

    if (env->ExceptionOccurred() != NULL) { return; }
    sz.ws_row = row;
    sz.ws_col = col;
    sz.ws_xpixel = xpixel;
    sz.ws_ypixel = ypixel;

    ioctl(fd, TIOCSWINSZ, &sz);
  }

  JNIEXPORT jint Java_org_mosh_MoshClient_waitFor(JNIEnv* env, jclass clazz,
                                                  jint procId) {
    int status;
    waitpid(procId, &status, 0);
    int result = 0;
    if (WIFEXITED(status)) {
      result = WEXITSTATUS(status);
    }
    return result;
  }

  JNIEXPORT jint JNICALL Java_org_mosh_MoshClient_kill(JNIEnv* env, jclass clazz,
                                                       jint pid, jint signal) {
    return kill(pid, signal);
  }

  static void create_mosh_client(char* ip, char* port, int* pPtm, int* pProcessId) {
    char devname[32];
    int ptm;
    pid_t pid;

    *pPtm = -1;

    ptm = open("/dev/ptmx", O_RDWR); // | O_NOCTTY);
    if(ptm < 0) {
      LOG_E("[ cannot open /dev/ptmx - %s ]\n", strerror(errno));
      return;
    }
    fcntl(ptm, F_SETFD, FD_CLOEXEC);

    if(
      unlockpt(ptm) ||
      ptsname_r(ptm, devname, sizeof(devname))){
      LOG_E("[ trouble with /dev/ptmx - %s ]\n", strerror(errno));
      return;
    }

    pid = fork();
    if(pid < 0) {
      LOG_E("- fork failed: %s -\n", strerror(errno));
      return;
    }

    if(pid == 0) {
      int pts;

      prctl(PR_SET_NAME, "MoshClient", 0, 0, 0);
      setsid();

      pts = open(devname, O_RDWR);
      if(pts < 0) exit(-1);

      dup2(pts, 0);
      dup2(pts, 1);
      dup2(pts, 2);

      close(ptm);

      int argc = 3;
      char *argv[argc+1];
      std::string cmd("mosh-client");
      argv[0] = &cmd[0];
      argv[1] = ip;
      argv[2] = port;
      argv[3] = NULL;

      std::cerr << "[ MoshClient.forkExec(" << ip << ", " << port << ")@main ]" << std::endl;
      mosh_client_main(argc, argv);
      std::cerr << "[ MoshClient.forkExec(" << ip << ", " << port << ")@exit ]" << std::endl;
      std::cerr << std::endl;

      fflush(stdout);
      fflush(stderr);

      _exit(1);
    } else {
      *pProcessId = (int) pid;
      *pPtm = ptm;
      return;
    }
  }

  JNIEXPORT jobject JNICALL Java_org_mosh_MoshClient_forkExec(JNIEnv* env, jclass clazz,
                                                              jstring arg0, jstring arg1) {
    LOG_I("[ MoshClient.forkExec(%p, %p) ]\n", arg0, arg1);
    std::unique_ptr<char[]> arg_ip = jniGetStringNativeChars(env, arg0);
    std::unique_ptr<char[]> arg_port = jniGetStringNativeChars(env, arg1);

    int procId;
    int ptm;
    std::thread(create_mosh_client, arg_ip.get(), arg_port.get(), &ptm, &procId).join();

    jclass Class_java_io_FileDescriptor = env->FindClass("java/io/FileDescriptor");
    if (Class_java_io_FileDescriptor == nullptr) {
      jniThrowByName(env, "java/lang/Exception", "Couldn't find java.io.FileDescriptor");
      return nullptr;
    }
    jmethodID fdInit = env->GetMethodID(Class_java_io_FileDescriptor, "<init>", "()V");
    jobject fd = env->NewObject(Class_java_io_FileDescriptor, fdInit);
    if (fd == nullptr) {
      jniThrowByName(env, "java/lang/Exception", "Couldn't create a FileDescriptor");
      return nullptr;
    } else {
      jfieldID descriptor = env->GetFieldID(Class_java_io_FileDescriptor, "descriptor", "I");
      env->SetIntField(fd, descriptor, ptm);
    }

    jlong pidValue = (jlong) procId;
    jclass Class_java_lang_Long = env->FindClass("java/lang/Long");
    jobject pid = env->NewObject(Class_java_lang_Long,
                                 env->GetMethodID(Class_java_lang_Long, "<init>", "(J)V"),
                                 pidValue);
    if (pid == nullptr) {
      jniThrowByName(env, "java/lang/Exception", "Couldn't create a Long");
      return nullptr;
    }

    jclass Class_android_util_Pair = env->FindClass("android/util/Pair");
    if (Class_android_util_Pair == nullptr) {
      jniThrowByName(env, "java/lang/Exception", "Couldn't find android.util.Pair");
      return nullptr;
    }
    jmethodID pairInit = env->GetMethodID(Class_android_util_Pair, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
    jobject pair = env->NewObject(Class_android_util_Pair, pairInit, fd, pid);
    if (pair == nullptr) {
      jniThrowByName(env, "java/lang/Exception", "Couldn't create an android.util.Pair");
      return nullptr;
    }

    return pair;
  }
}
