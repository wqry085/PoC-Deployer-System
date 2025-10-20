#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <errno.h>
#include <termios.h>
#include <android/log.h>
#include <stdatomic.h>
#define LOG_TAG "TerminalNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// 对于较老的Android版本，实现ptsname_r
#if __ANDROID_API__ < 21
#include <stdio.h>

static int ptsname_r(int fd, char* buffer, size_t size) {
    char* name = ptsname(fd);
    if (!name) {
        return -1;
    }
    if (strlen(name) >= size) {
        return -1;
    }
    strcpy(buffer, name);
    return 0;
}
#endif

typedef struct TerminalProcess {
    int master_fd;
    pid_t pid;
    atomic_bool running;
    pthread_t output_thread;
    JavaVM* jvm;
    jobject java_callback;
} TerminalProcess;

// 设置终端属性
static void setup_terminal(int fd) {
    struct termios termios;
    if (tcgetattr(fd, &termios) == 0) {
        // 启用回显和规范模式
        termios.c_lflag |= ECHO | ICANON;
        
        // 设置控制字符
        termios.c_cc[VINTR] = 3;    // Ctrl+C
        termios.c_cc[VQUIT] = 28;   // Ctrl+\\
        termios.c_cc[VERASE] = 127; // Backspace
        termios.c_cc[VKILL] = 21;   // Ctrl+U
        termios.c_cc[VEOF] = 4;     // Ctrl+D
        
        if (tcsetattr(fd, TCSANOW, &termios) < 0) {
            LOGW("tcsetattr failed: %s", strerror(errno));
        }
    } else {
        LOGW("tcgetattr failed: %s", strerror(errno));
    }
}

static void* output_thread_func(void* arg) {
    TerminalProcess* process = (TerminalProcess*)arg;
    if (!process || !process->jvm) {
        LOGE("Invalid process in output thread");
        return NULL;
    }

    JNIEnv* env;
    // 修正：使用正确的JavaVM函数调用语法
    jint result = (*process->jvm)->AttachCurrentThread(process->jvm, &env, NULL);
    if (result != JNI_OK) {
        LOGE("Failed to attach thread to JVM: %d", result);
        return NULL;
    }

    char buffer[4096];
    while (atomic_load(&process->running)) {
        if (process->master_fd <= 0) {
            LOGW("Invalid master FD");
            break;
        }

        fd_set read_fds;
        FD_ZERO(&read_fds);
        FD_SET(process->master_fd, &read_fds);

        struct timeval timeout = {0, 100000}; // 100ms

        int select_result = select(process->master_fd + 1, &read_fds, NULL, NULL, &timeout);
        if (select_result > 0 && FD_ISSET(process->master_fd, &read_fds)) {
            ssize_t bytes_read = read(process->master_fd, buffer, sizeof(buffer) - 1);
            if (bytes_read > 0) {
                buffer[bytes_read] = '\0';
                
                jclass cls = (*env)->GetObjectClass(env, process->java_callback);
                if (!cls) {
                    LOGE("Failed to get callback class");
                    continue;
                }

                jmethodID method = (*env)->GetMethodID(env, cls, "onNativeOutput", "([BI)V");
                if (!method) {
                    LOGE("Failed to get callback method");
                    (*env)->DeleteLocalRef(env, cls);
                    continue;
                }

                jbyteArray data = (*env)->NewByteArray(env, bytes_read);
                if (data) {
                    (*env)->SetByteArrayRegion(env, data, 0, bytes_read, (jbyte*)buffer);
                    (*env)->CallVoidMethod(env, process->java_callback, method, data, bytes_read);
                    (*env)->DeleteLocalRef(env, data);
                }

                (*env)->DeleteLocalRef(env, cls);

                if ((*env)->ExceptionCheck(env)) {
                    (*env)->ExceptionClear(env);
                }
            } else if (bytes_read == 0) {
                LOGI("Process output closed");
                break;
            } else if (bytes_read < 0) {
                if (errno != EAGAIN && errno != EWOULDBLOCK) {
                    LOGE("Read error: %s", strerror(errno));
                    break;
                }
            }
        } else if (select_result < 0) {
            if (errno != EINTR) {
                LOGE("Select error: %s", strerror(errno));
                break;
            }
        }
    }

    // 修正：使用正确的JavaVM函数调用语法
    (*process->jvm)->DetachCurrentThread(process->jvm);
    LOGI("Output thread exited");
    return NULL;
}

JNIEXPORT jlong JNICALL
Java_com_wqry085_deployesystem_TerminalSession_nativeStartSession(
    JNIEnv* env, jobject thiz, jstring shell, jobjectArray args, jobjectArray env_vars) {
    
    const char* shell_path = (*env)->GetStringUTFChars(env, shell, NULL);
    if (!shell_path) {
        LOGE("Failed to get shell path");
        return 0;
    }

    LOGI("Starting terminal: %s", shell_path);

    // 创建主终端
    int master_fd = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (master_fd < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, shell, shell_path);
        return 0;
    }

    // 解锁从终端
    if (unlockpt(master_fd) < 0) {
        LOGE("unlockpt failed: %s", strerror(errno));
        close(master_fd);
        (*env)->ReleaseStringUTFChars(env, shell, shell_path);
        return 0;
    }

    // 获取从终端名称
    char slave_name[256];
    int pts_result = ptsname_r(master_fd, slave_name, sizeof(slave_name));
    if (pts_result != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(master_fd);
        (*env)->ReleaseStringUTFChars(env, shell, shell_path);
        return 0;
    }

    LOGI("Master FD: %d, Slave PTY: %s", master_fd, slave_name);

    pid_t pid = fork();
    if (pid == 0) { // 子进程
        close(master_fd);

        // 创建新的会话
        if (setsid() < 0) {
            LOGE("setsid failed: %s", strerror(errno));
            _exit(EXIT_FAILURE);
        }

        // 打开从终端
        int slave_fd = open(slave_name, O_RDWR);
        if (slave_fd < 0) {
            LOGE("Failed to open slave PTY %s: %s", slave_name, strerror(errno));
            _exit(EXIT_FAILURE);
        }

        // 设置控制终端
        if (ioctl(slave_fd, TIOCSCTTY, 0) < 0) {
            LOGW("ioctl(TIOCSCTTY) failed: %s", strerror(errno));
        }

        // 重定向标准输入输出
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        if (slave_fd > STDERR_FILENO) {
            close(slave_fd);
        }

        // 设置终端属性
        setup_terminal(STDIN_FILENO);

        // 设置窗口大小
        struct winsize ws;
        ws.ws_row = 24;
        ws.ws_col = 80;
        ws.ws_xpixel = 0;
        ws.ws_ypixel = 0;
        ioctl(STDIN_FILENO, TIOCSWINSZ, &ws);

        // 设置环境变量
        setenv("TERM", "xterm-256color", 1);
        setenv("PS1", "\\w \\$ ", 1);
        setenv("HOME", "/data/local/tmp", 1);
        setenv("USER", "shell", 1);
        setenv("SHELL", shell_path, 1);
        setenv("PATH", "/system/bin:/system/xbin:/vendor/bin:/sbin", 1);

        // 执行shell
        execlp(shell_path, shell_path, "-i", NULL);

        // 备用方案
        execlp("/system/bin/sh", "sh", "-i", NULL);
        execlp("/bin/sh", "sh", "-i", NULL);

        LOGE("exec failed: %s", strerror(errno));
        _exit(127);

    } else if (pid > 0) { // 父进程
        (*env)->ReleaseStringUTFChars(env, shell, shell_path);

        // 设置非阻塞模式
        int flags = fcntl(master_fd, F_GETFL, 0);
        if (fcntl(master_fd, F_SETFL, flags | O_NONBLOCK) < 0) {
            LOGW("Failed to set non-blocking mode: %s", strerror(errno));
        }

        TerminalProcess* process = (TerminalProcess*)malloc(sizeof(TerminalProcess));
        if (!process) {
            LOGE("Failed to allocate TerminalProcess");
            close(master_fd);
            return 0;
        }
        
        process->master_fd = master_fd;
        process->pid = pid;
        atomic_store(&process->running, true);

        if ((*env)->GetJavaVM(env, &process->jvm) != JNI_OK) {
            LOGE("Failed to get Java VM");
            close(master_fd);
            free(process);
            return 0;
        }

        process->java_callback = (*env)->NewGlobalRef(env, thiz);
        if (!process->java_callback) {
            LOGE("Failed to create global reference");
            close(master_fd);
            free(process);
            return 0;
        }

        int thread_result = pthread_create(&process->output_thread, NULL, output_thread_func, process);
        if (thread_result != 0) {
            LOGE("Failed to start output thread: %s", strerror(thread_result));
            (*env)->DeleteGlobalRef(env, process->java_callback);
            close(master_fd);
            free(process);
            return 0;
        }

        LOGI("Terminal started successfully, PID: %d", pid);
        return (jlong)process;

    } else {
        LOGE("Fork failed: %s", strerror(errno));
        close(master_fd);
        (*env)->ReleaseStringUTFChars(env, shell, shell_path);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_wqry085_deployesystem_TerminalSession_nativeWriteInput(
    JNIEnv* env, jobject thiz, jlong process_ptr, jbyteArray data) {
    
    TerminalProcess* process = (TerminalProcess*)(intptr_t)process_ptr;
    if (!process || !atomic_load(&process->running) || process->master_fd <= 0) {
        LOGE("Invalid process or not running");
        return;
    }

    jsize length = (*env)->GetArrayLength(env, data);
    if (length <= 0) {
        return;
    }

    jbyte* buffer = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buffer) {
        LOGE("Failed to get byte array elements");
        return;
    }

    ssize_t written = write(process->master_fd, buffer, length);
    if (written < 0) {
        LOGE("Write failed: %s", strerror(errno));
    }

    (*env)->ReleaseByteArrayElements(env, data, buffer, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_wqry085_deployesystem_TerminalSession_nativeStopSession(
    JNIEnv* env, jobject thiz, jlong process_ptr) {
    
    TerminalProcess* process = (TerminalProcess*)(intptr_t)process_ptr;
    if (!process) {
        return;
    }

    LOGI("Stopping terminal session");

    atomic_store(&process->running, false);

    if (process->output_thread) {
        pthread_join(process->output_thread, NULL);
    }

    if (process->master_fd > 0) {
        close(process->master_fd);
    }

    if (process->pid > 0) {
        kill(process->pid, SIGTERM);
        int status;
        waitpid(process->pid, &status, 0);
    }

    if (process->java_callback) {
        (*env)->DeleteGlobalRef(env, process->java_callback);
    }

    free(process);
    LOGI("Terminal session stopped");
}

JNIEXPORT jboolean JNICALL
Java_com_wqry085_deployesystem_TerminalSession_nativeIsRunning(
    JNIEnv* env, jobject thiz, jlong process_ptr) {
    
    TerminalProcess* process = (TerminalProcess*)(intptr_t)process_ptr;
    return (process && atomic_load(&process->running)) ? JNI_TRUE : JNI_FALSE;
}