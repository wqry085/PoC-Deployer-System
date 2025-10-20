#ifndef APP_PROCESS_LAUNCHER_H
#define APP_PROCESS_LAUNCHER_H

#ifdef __cplusplus
extern "C" {
#endif

int app_process_start(const char *nice_name,
                      const char *java_classpath,
                      const char *main_class,
                      char *const argv[]);

int app_process_wait(int pid);

int app_process_kill(int pid);

#ifdef __cplusplus
}
#endif

#endif