#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>


#define MAX_PATH_LENGTH 4096
#define MAX_CMD_LENGTH 16384

#define PACKAGE_NAME "com.wqry085.deployesystem"
#define MAIN_CLASS "com.wqry085.deployesystem.next.Binder"
#define VERSION "2.0.0"



static char* trim(char* str) {
    if (str == NULL) return NULL;
    
    while (*str == ' ' || *str == '\t' || *str == '\n' || *str == '\r') {
        str++;
    }
    
    if (*str == '\0') return str;
    
    char* end = str + strlen(str) - 1;
    while (end > str && (*end == ' ' || *end == '\t' || *end == '\n' || *end == '\r')) {
        *end = '\0';
        end--;
    }
    
    return str;
}

static int file_readable(const char* path) {
    return path != NULL && path[0] != '\0' && access(path, R_OK) == 0;
}

static int file_executable(const char* path) {
    return path != NULL && path[0] != '\0' && access(path, X_OK) == 0;
}



static char* get_apk_from_env(void) {
    char* path = getenv("BINDER_APK");
    if (path != NULL && path[0] != '\0') {
        if (file_readable(path)) {
            return path;
        }
        fprintf(stderr, "Warning: BINDER_APK set but not accessible: %s\n", path);
    }
    return NULL;
}

static char* get_apk_from_pm(void) {
    static char apk_path[MAX_PATH_LENGTH];
    char command[256];
    
    snprintf(command, sizeof(command), "pm path %s 2>/dev/null", PACKAGE_NAME);
    
    FILE* fp = popen(command, "r");
    if (fp == NULL) {
        return NULL;
    }
    
    char* result = NULL;
    if (fgets(apk_path, sizeof(apk_path), fp) != NULL) {
        char* colon = strchr(apk_path, ':');
        if (colon != NULL) {
            result = trim(colon + 1);
            if (file_readable(result)) {
                pclose(fp);
                return result;
            }
        }
    }
    
    pclose(fp);
    return NULL;
}

static char* get_apk_path(void) {
    char* path;
    
    path = get_apk_from_env();
    if (path != NULL) return path;
    
    path = get_apk_from_pm();
    if (path != NULL) return path;
    
    return NULL;
}



static const char* find_app_process(void) {
    
    if (file_executable("/system/bin/app_process64")) {
        return "/system/bin/app_process64";
    }
    if (file_executable("/system/bin/app_process")) {
        return "/system/bin/app_process";
    }
    if (file_executable("/system/bin/app_process32")) {
        return "/system/bin/app_process32";
    }
    return NULL;
}


static int get_sdk_version(void) {
    FILE* fp = popen("getprop ro.build.version.sdk 2>/dev/null", "r");
    if (fp == NULL) return 0;
    
    char buf[16];
    int sdk = 0;
    if (fgets(buf, sizeof(buf), fp) != NULL) {
        sdk = atoi(trim(buf));
    }
    pclose(fp);
    return sdk;
}


static int build_vm_args(char* buffer, size_t size, const char* apk_path) {
    int sdk = get_sdk_version();
    int offset = 0;
    
    
    offset += snprintf(buffer + offset, size - offset,
        "-Djava.class.path=%s", apk_path);
    
    
    offset += snprintf(buffer + offset, size - offset,
        " -Xnoimage-dex2oat");
    
    
    if (sdk >= 24) { 
        
        offset += snprintf(buffer + offset, size - offset,
            " -Xusejit:false");
    }
    
    if (sdk >= 28) { 
        
        offset += snprintf(buffer + offset, size - offset,
            " -Xcompiler-option --compiler-filter=assume-verified");
    }
    
    
    return offset;
}


static int execute_binder(const char* apk_path, int argc, char** argv) {
    const char* app_process = find_app_process();
    if (app_process == NULL) {
        fprintf(stderr, "Error: app_process not found\n");
        return 1;
    }
    
    char cmd[MAX_CMD_LENGTH];
    char vm_args[2048];
    int offset;
    
    
    build_vm_args(vm_args, sizeof(vm_args), apk_path);
    
    
    offset = snprintf(cmd, sizeof(cmd),
        "%s %s /system/bin %s",
        app_process, vm_args, MAIN_CLASS);
    
    
    for (int i = 1; i < argc; i++) {
        int remaining = sizeof(cmd) - offset - 1;
        int arg_len = strlen(argv[i]);
        
        if (remaining < arg_len + 4) {
            fprintf(stderr, "Error: Command too long\n");
            return 1;
        }
        
        
        int need_quote = 0;
        for (const char* p = argv[i]; *p; p++) {
            if (*p == ' ' || *p == '\t' || *p == '"' || *p == '\'' || 
                *p == '\\' || *p == '$' || *p == '`' || *p == '!' ||
                *p == '*' || *p == '?' || *p == '[' || *p == ']' ||
                *p == '(' || *p == ')' || *p == '{' || *p == '}' ||
                *p == '|' || *p == '&' || *p == ';' || *p == '<' ||
                *p == '>' || *p == '\n') {
                need_quote = 1;
                break;
            }
        }
        
        if (need_quote) {
            
            offset += snprintf(cmd + offset, remaining, " '");
            for (const char* p = argv[i]; *p; p++) {
                if (*p == '\'') {
                    offset += snprintf(cmd + offset, sizeof(cmd) - offset, "'\"'\"'");
                } else {
                    cmd[offset++] = *p;
                }
            }
            offset += snprintf(cmd + offset, sizeof(cmd) - offset, "'");
        } else {
            offset += snprintf(cmd + offset, remaining, " %s", argv[i]);
        }
    }
    
    
    return system(cmd);
}



static void print_help(const char* prog) {
    printf("Binder CLI Launcher v%s\n\n", VERSION);
    printf("Usage: %s [command] [args...]\n\n", prog);
    printf("Launcher options:\n");
    printf("  --version     Show version\n");
    printf("  --path        Show APK path\n");
    printf("  --sdk         Show SDK version\n");
    printf("  --help        Show this help\n\n");
    printf("Environment:\n");
    printf("  BINDER_APK    Custom APK/JAR path\n\n");
    printf("Commands (passed to Java):\n");
    printf("  list, ls                  List services\n");
    printf("  methods, m <service>      List methods\n");
    printf("  call, c <service> <method> [args]\n");
    printf("  info, i <service>         Service info\n");
    printf("  ping, p <service>         Ping service\n");
    printf("  dump, d <service>         Dump service\n");
    printf("  monitor                   Monitor services\n");
    printf("  search <pattern>          Search services\n");
    printf("  interface <name>          Interface info\n");
    printf("  shell                     Interactive mode\n");
    printf("  help                      Full help\n");
}



int main(int argc, char** argv) {
    
    if (argc >= 2) {
        if (strcmp(argv[1], "--version") == 0) {
            printf("Binder CLI v%s (SDK %d)\n", VERSION, get_sdk_version());
            return 0;
        }
        if (strcmp(argv[1], "--path") == 0) {
            char* path = get_apk_path();
            if (path) {
                printf("%s\n", path);
                return 0;
            }
            fprintf(stderr, "APK not found\n");
            return 1;
        }
        if (strcmp(argv[1], "--sdk") == 0) {
            printf("%d\n", get_sdk_version());
            return 0;
        }
        if (strcmp(argv[1], "--help") == 0 && argc == 2) {
            print_help(argv[0]);
            return 0;
        }
    }
    
    
    char* apk_path = get_apk_path();
    if (apk_path == NULL) {
        fprintf(stderr, "Error: Cannot find Binder CLI\n\n");
        fprintf(stderr, "Solutions:\n");
        fprintf(stderr, "  1. Install APK:\n");
        fprintf(stderr, "     pm install binder-cli.apk\n\n");
        fprintf(stderr, "  2. Set BINDER_APK:\n");
        fprintf(stderr, "     export BINDER_APK=/path/to/binder.jar\n");
        return 1;
    }
    
     return execute_binder(apk_path, argc, argv);
}