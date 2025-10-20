#include "app_process_launcher.h"
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/wait.h>
#include <signal.h>
#include <dirent.h>
#include <string.h>

static char *build_default_classpath() {
    const char *framework_dir = "/system/framework";
    DIR *dir = opendir(framework_dir);
    if (!dir) return NULL;

    size_t buf_size = 8192;
    char *classpath = malloc(buf_size);
    if (!classpath) return NULL;
    classpath[0] = '\0';

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strstr(entry->d_name, ".jar")) {
            char path[512];
            snprintf(path, sizeof(path), "%s/%s", framework_dir, entry->d_name);

            if (strlen(classpath) + strlen(path) + 2 > buf_size) {
                buf_size *= 2;
                classpath = realloc(classpath, buf_size);
                if (!classpath) {
                    closedir(dir);
                    return NULL;
                }
            }
            if (classpath[0] != '\0') strcat(classpath, ":");
            strcat(classpath, path);
        }
    }
    closedir(dir);
    return classpath;
}

int app_process_start(const char *nice_name,
                      const char *java_classpath,
                      const char *main_class,
                      char *const argv[]) {
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        const char *app_process_bin = "/system/bin/app_process";
        if (nice_name) setenv("ANDROID_APP_NAME", nice_name, 1);

        char *classpath = NULL;
        if (java_classpath && strlen(java_classpath) > 0) {
            classpath = strdup(java_classpath);
        } else {
            classpath = build_default_classpath();
        }
        if (classpath) {
            setenv("CLASSPATH", classpath, 1);
            free(classpath);
        }

        execlp(app_process_bin, app_process_bin,
               "/system/bin",
               main_class,
               argv ? argv[0] : NULL,
               (char *)NULL);

        _exit(127);
    }
    return pid;
}

int app_process_wait(int pid) {
    int status;
    if (waitpid(pid, &status, 0) == -1) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

int app_process_kill(int pid) {
    if (kill(pid, SIGKILL) == -1) return -1;
    return 0;
}