#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <pwd.h>
#include <grp.h>
#include <arpa/inet.h>
#include <termios.h>
#include <signal.h>
#include <pty.h>
#include <utmp.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <pthread.h>
#include "app_process_launcher.h"
#include "socket_sender.h"  // 添加socket_sender头文件

#define MAX_CONNECTIONS 10
#define BUFFER_SIZE 4096
#define CONTROL_KEY "df2a17ef1e6070522c563bac29933e58"
#define MAX_HISTORY 1000
#define DEFAULT_APP_DIR_PORT 8082  // 默认的app-dir传输端口

typedef struct {
    char command_history[MAX_HISTORY][BUFFER_SIZE];
    int history_count;
    pid_t current_pid;
    pid_t shell_server_pid;
    pid_t control_server_pid;
    pid_t main_pid;
    int should_terminate;
    char java_class[BUFFER_SIZE];  // 要启动的Java类和方法
    char java_path[BUFFER_SIZE];   // Java应用的路径
    char app_dir[BUFFER_SIZE];     // 要发送的应用程序目录路径
    int app_dir_port;              // app-dir传输端口
} ControlSystem;

ControlSystem control_system = {0};

void sigchld_handler(int sig) {
    while (waitpid(-1, NULL, WNOHANG) > 0);
}

void sigterm_handler(int sig) {
    control_system.should_terminate = 1;
    printf("Received termination signal, shutting down...\n");
}

void kill_all_related_processes() {
    printf("Killing all related processes...\n");
    
    if (control_system.shell_server_pid > 0) {
        printf("Killing shell server (PID: %d)\n", control_system.shell_server_pid);
        kill(control_system.shell_server_pid, SIGKILL);
    }
    
    if (control_system.control_server_pid > 0) {
        printf("Killing control server (PID: %d)\n", control_system.control_server_pid);
        kill(control_system.control_server_pid, SIGKILL);
    }
    
    system("pkill -f zygote_term");
    system("pkill -f start_shell_server");
    
    if (control_system.main_pid > 0) {
        printf("Killing main process (PID: %d)\n", control_system.main_pid);
        kill(control_system.main_pid, SIGKILL);
    }
}

// 新增：获取应用路径
int get_app_path(const char *package_name, char *path_buffer, size_t buffer_size) {
    FILE *fp;
    char command[BUFFER_SIZE];
    char buffer[BUFFER_SIZE];
    
    snprintf(command, sizeof(command), "pm path %s", package_name);
    
    printf("Executing: %s\n", command);
    
    fp = popen(command, "r");
    if (fp == NULL) {
        printf("Failed to execute pm path command\n");
        return 0;
    }
    
    if (fgets(buffer, sizeof(buffer), fp) != NULL) {
        // 解析输出：package:/path/to/apk
        char *start = strstr(buffer, "package:");
        if (start) {
            start += 8; // 跳过"package:"
            char *end = strchr(start, '\n');
            if (end) *end = '\0';
            
            strncpy(path_buffer, start, buffer_size - 1);
            path_buffer[buffer_size - 1] = '\0';
            pclose(fp);
            return 1;
        }
    }
    
    pclose(fp);
    return 0;
}

void start_java_application() {
    if (strlen(control_system.java_class) == 0) {
        printf("Error: Java class not specified\n");
        return;
    }

    printf("Starting Java application: %s with class: %s\n",
           control_system.java_path,
           control_system.java_class);

    char *args[] = { NULL };

    int pid = app_process_start("socket-java",
                                control_system.java_path,
                                control_system.java_class,
                                args);
    if (pid < 0) {
        printf("Failed to start Java application\n");
        return;
    }

    int exit_code = app_process_wait(pid);
    printf("Java application exited with code: %d\n", exit_code);
}

// 新增：发送应用程序目录
void send_app_directory() {
    if (strlen(control_system.app_dir) == 0) {
        printf("Error: No app directory specified\n");
        return;
    }
    
    printf("Sending app directory: %s to port %d\n", 
           control_system.app_dir, control_system.app_dir_port);
    
    SendStatus status = send_folder_to_local_port(control_system.app_dir, 
                                                 control_system.app_dir_port);
    
    switch (status) {
        case SEND_STATUS_SUCCESS:
            printf("App directory sent successfully\n");
            break;
        case SEND_STATUS_PORT_NOT_OPEN:
            printf("Error: Port %d is not open\n", control_system.app_dir_port);
            break;
        case SEND_STATUS_FOLDER_NOT_FOUND:
            printf("Error: App directory '%s' not found\n", control_system.app_dir);
            break;
        case SEND_STATUS_SOCKET_ERROR:
            printf("Error: Socket error occurred\n");
            break;
        case SEND_STATUS_SEND_ERROR:
            printf("Error: Send error occurred\n");
            break;
        case SEND_STATUS_PROTOCOL_ERROR:
            printf("Error: Protocol error occurred\n");
            break;
        default:
            printf("Error: Unknown error occurred\n");
            break;
    }
}

// 修改：解析命令行参数，添加app-dir支持
void parse_arguments(int argc, char *argv[]) {
    int i;
    for (i = 1; i < argc; i++) {
        if (strncmp(argv[i], "--Classjava-socket=", 19) == 0) {
            // 解析Java类和方法
            const char *class_method = argv[i] + 19;
            strncpy(control_system.java_class, class_method, sizeof(control_system.java_class) - 1);
            control_system.java_class[sizeof(control_system.java_class) - 1] = '\0';
            printf("Java class set to: %s\n", control_system.java_class);
        }
        else if (strncmp(argv[i], "--Classjava-path=", 17) == 0) {
            // 直接指定Java路径
            const char *path = argv[i] + 17;
            strncpy(control_system.java_path, path, sizeof(control_system.java_path) - 1);
            control_system.java_path[sizeof(control_system.java_path) - 1] = '\0';
            printf("Java path set to: %s\n", control_system.java_path);
        }
        else if (strncmp(argv[i], "--package=", 10) == 0) {
            // 通过包名自动获取路径
            const char *package_name = argv[i] + 10;
            if (get_app_path(package_name, control_system.java_path, sizeof(control_system.java_path))) {
                printf("Auto-detected Java path: %s\n", control_system.java_path);
            } else {
                printf("Failed to get path for package: %s\n", package_name);
            }
        }
        else if (strncmp(argv[i], "--app-dir=", 10) == 0) {
            // 解析app-dir参数
            const char *app_dir = argv[i] + 10;
            strncpy(control_system.app_dir, app_dir, sizeof(control_system.app_dir) - 1);
            control_system.app_dir[sizeof(control_system.app_dir) - 1] = '\0';
            printf("App directory set to: %s\n", control_system.app_dir);
            
            // 检查是否有端口指定（格式：--app-dir=/path:port）
            char *port_separator = strchr(control_system.app_dir, ':');
            if (port_separator != NULL) {
                *port_separator = '\0'; // 分隔路径和端口
                control_system.app_dir_port = atoi(port_separator + 1);
                printf("App directory port set to: %d\n", control_system.app_dir_port);
            } else {
                control_system.app_dir_port = DEFAULT_APP_DIR_PORT;
                printf("Using default app directory port: %d\n", control_system.app_dir_port);
            }
        }
    }
}

int check_permission(uid_t required_uid) {
    return getuid() == required_uid;
}

void setup_environment() {
    struct passwd *pw = getpwuid(getuid());
    if (pw) {
        setenv("USER", pw->pw_name, 1);
        setenv("LOGNAME", pw->pw_name, 1);
        setenv("HOME", pw->pw_dir, 1);
    }
    setenv("TERM", "xterm-256color", 1);
}

void add_to_history(const char *command) {
    if (strlen(command) == 0) return;
    
    if (control_system.history_count < MAX_HISTORY) {
        strncpy(control_system.command_history[control_system.history_count], 
                command, BUFFER_SIZE - 1);
        control_system.command_history[control_system.history_count][BUFFER_SIZE - 1] = '\0';
        control_system.history_count++;
    } else {
        for (int i = 0; i < MAX_HISTORY - 1; i++) {
            strcpy(control_system.command_history[i], control_system.command_history[i + 1]);
        }
        strncpy(control_system.command_history[MAX_HISTORY - 1], 
                command, BUFFER_SIZE - 1);
        control_system.command_history[MAX_HISTORY - 1][BUFFER_SIZE - 1] = '\0';
    }
}

void execute_shell_command(int client_socket, const char *command) {
    FILE *fp;
    char buffer[BUFFER_SIZE];
    char safe_command[BUFFER_SIZE * 2];
    
    if (strlen(command) == 0) {
        send(client_socket, "ERROR: Empty command\n", 21, 0);
        return;
    }
    
    snprintf(safe_command, sizeof(safe_command), "LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 %s 2>&1", command);
    
    char log_message[BUFFER_SIZE];
    snprintf(log_message, sizeof(log_message), "EXEC: %s", command);
    add_to_history(log_message);
    
    printf("Executing command: %s\n", command);
    
    fp = popen(safe_command, "r");
    if (fp == NULL) {
        send(client_socket, "ERROR: Failed to execute command\n", 33, 0);
        return;
    }
    
    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        send(client_socket, buffer, strlen(buffer), 0);
    }
    
    send(client_socket, "COMMAND_COMPLETED\n", 18, 0);
    pclose(fp);
}

int authenticate_control_connection(int client_socket) {
    char buffer[BUFFER_SIZE];
    int bytes_read = recv(client_socket, buffer, sizeof(buffer) - 1, 0);
    
    if (bytes_read > 0) {
        buffer[bytes_read] = '\0';
        buffer[strcspn(buffer, "\r\n")] = '\0';
        
        if (strcmp(buffer, CONTROL_KEY) == 0) {
            send(client_socket, "AUTH_SUCCESS\n", 13, 0);
            return 1;
        }
    }
    
    send(client_socket, "AUTH_FAILED\n", 12, 0);
    return 0;
}

void terminate_whole_system() {
    printf("Terminating entire server system completely...\n");
    
    pid_t main_pid = control_system.main_pid;
    
    if (control_system.shell_server_pid > 0) {
        printf("Killing shell server (PID: %d)\n", control_system.shell_server_pid);
        kill(control_system.shell_server_pid, SIGKILL);
    }
    
    if (control_system.control_server_pid > 0) {
        printf("Killing control server (PID: %d)\n", control_system.control_server_pid);
        kill(control_system.control_server_pid, SIGKILL);
    }
    
    sleep(1);
    
    system("pkill -KILL -f zygote_term 2>/dev/null");
    system("pkill -KILL -f 'start_shell_server' 2>/dev/null");
    
    if (getpid() != main_pid) {
        exit(EXIT_SUCCESS);
    }
    
    printf("Complete system termination finished.\n");
    exit(EXIT_SUCCESS);
}

void handle_control_command(int client_socket, const char *command) {
    char response[BUFFER_SIZE * 10] = {0};
    
    add_to_history(command);
    
    if (strncmp(command, "EXEC ", 5) == 0) {
        const char *shell_command = command + 5;
        execute_shell_command(client_socket, shell_command);
    }
    else if (strcmp(command, "GET_HISTORY") == 0) {
        if (control_system.history_count == 0) {
            strcpy(response, "No command history available\n");
        } else {
            for (int i = 0; i < control_system.history_count; i++) {
                snprintf(response + strlen(response), sizeof(response) - strlen(response),
                        "%d: %s\n", i + 1, control_system.command_history[i]);
            }
        }
        strncat(response, "END_OF_HISTORY\n", sizeof(response) - strlen(response) - 1);
        send(client_socket, response, strlen(response), 0);
    }
    else if (strcmp(command, "TERMINATE") == 0) {
        send(client_socket, "SYSTEM_TERMINATING_COMPLETELY\n", 30, 0);
        usleep(100000);
        terminate_whole_system();
    }
    else if (strcmp(command, "KILL_ALL") == 0) {
        send(client_socket, "KILLING_ALL_PROCESSES\n", 22, 0);
        usleep(100000);
        kill_all_related_processes();
    }
    else if (strcmp(command, "START_JAVA") == 0) {
        // 新增：通过控制命令启动Java应用
        send(client_socket, "STARTING_JAVA_APPLICATION\n", 26, 0);
        start_java_application();
    }
    else if (strcmp(command, "SEND_APP_DIR") == 0) {
        // 新增：通过控制命令发送app目录
        send(client_socket, "SENDING_APP_DIRECTORY\n", 22, 0);
        send_app_directory();
    }
    else if (strcmp(command, "STATUS") == 0) {
        uid_t uid = getuid();
        gid_t gid = getgid();
        pid_t pid = getpid();
        
        struct passwd *pw = getpwuid(uid);
        struct group *gr = getgrgid(gid);
        
        char *username = pw ? pw->pw_name : "unknown";
        char *groupname = gr ? gr->gr_name : "unknown";
        
        snprintf(response, sizeof(response), 
                "Status: RUNNING\n"
                "Main PID: %d\n"
                "Shell Server PID: %d\n"
                "Control Server PID: %d\n"
                "Java Class: %s\n"
                "Java Path: %s\n"
                "App Directory: %s\n"
                "App Directory Port: %d\n"
                "UID: %d (%s)\n"
                "GID: %d (%s)\n"
                "PID: %d\n"
                "History count: %d\n"
                "END_OF_STATUS\n",
                control_system.main_pid,
                control_system.shell_server_pid,
                control_system.control_server_pid,
                control_system.java_class,
                control_system.java_path,
                control_system.app_dir,
                control_system.app_dir_port,
                uid, username,
                gid, groupname,
                pid,
                control_system.history_count);
        
        send(client_socket, response, strlen(response), 0);
    }
    else if (strcmp(command, "EXIT") == 0) {
        send(client_socket, "CONTROL_EXIT_ACK\n", 17, 0);
    }
    else {
        send(client_socket, "UNKNOWN_COMMAND\n", 16, 0);
    }
}

void* handle_control_connection(void* arg) {
    int client_socket = *(int*)arg;
    free(arg);
    
    if (!authenticate_control_connection(client_socket)) {
        close(client_socket);
        return NULL;
    }
    
    char buffer[BUFFER_SIZE];
    int bytes_read;
    
    send(client_socket, "CONTROL_CONNECTED\n", 18, 0);
    
    while ((bytes_read = recv(client_socket, buffer, sizeof(buffer) - 1, 0)) > 0) {
        buffer[bytes_read] = '\0';
        buffer[strcspn(buffer, "\r\n")] = '\0';
        
        if (strcmp(buffer, "EXIT") == 0) {
            send(client_socket, "CONTROL_EXIT_ACK\n", 17, 0);
            break;
        }
        
        handle_control_command(client_socket, buffer);
        
        if (strcmp(buffer, "TERMINATE") != 0 && strcmp(buffer, "KILL_ALL") != 0 && 
            strcmp(buffer, "START_JAVA") != 0 && strcmp(buffer, "SEND_APP_DIR") != 0) {
            send(client_socket, "COMMAND_PROCESSED\n", 18, 0);
        }
    }
    
    close(client_socket);
    return NULL;
}


void handle_shell_client(int client_socket) {
    control_system.current_pid = getpid();
    setup_environment();
    
    dup2(client_socket, STDIN_FILENO);
    dup2(client_socket, STDOUT_FILENO);
    dup2(client_socket, STDERR_FILENO);
    close(client_socket);
    
    struct termios term;
    if (tcgetattr(STDIN_FILENO, &term) == 0) {
        term.c_lflag |= ECHO;
        term.c_lflag |= ISIG;
        term.c_lflag &= ~ICANON;
        term.c_cc[VMIN] = 1;
        term.c_cc[VTIME] = 0;
        tcsetattr(STDIN_FILENO, TCSANOW, &term);
    }
    
    setenv("TERM", "xterm-256color", 1);
    
    char *shell = getenv("SHELL");
    if (shell == NULL) shell = "/system/bin/sh";
    
    execl(shell, shell, "-i", (char *)NULL);
    exit(EXIT_FAILURE);
}

void start_control_server(int port) {
    int server_socket, client_socket;
    struct sockaddr_in server_addr, client_addr;
    socklen_t client_len = sizeof(client_addr);
    signal(SIGTERM, sigterm_handler);

    if ((server_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Control socket");
        exit(EXIT_FAILURE);
    }

    int opt = 1;
    setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(port);

    if (bind(server_socket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Control bind");
        close(server_socket);
        exit(EXIT_FAILURE);
    }

    if (listen(server_socket, MAX_CONNECTIONS) < 0) {
        perror("Control listen");
        close(server_socket);
        exit(EXIT_FAILURE);
    }

    printf("Control server started on port %d (PID: %d)\n", port, getpid());

    while (!control_system.should_terminate) {
        client_socket = accept(server_socket, (struct sockaddr *)&client_addr, &client_len);
        if (client_socket < 0) {
            if (control_system.should_terminate) break;
            perror("Control accept");
            continue;
        }

        printf("Control client connected from %s:%d\n", 
               inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port));

        pthread_t thread;
        int *client_ptr = malloc(sizeof(int));
        *client_ptr = client_socket;
        
        if (pthread_create(&thread, NULL, handle_control_connection, client_ptr) != 0) {
            perror("Thread create");
            close(client_socket);
            free(client_ptr);
        } else {
            pthread_detach(thread);
        }
    }

    close(server_socket);
    printf("Control server shutdown complete\n");
    exit(EXIT_SUCCESS);
}

void start_shell_server(int port) {
    int server_socket, client_socket;
    struct sockaddr_in server_addr, client_addr;
    socklen_t client_len = sizeof(client_addr);
    pid_t pid;

    struct sigaction sa;
    sa.sa_handler = sigchld_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) == -1) {
        perror("sigaction");
        exit(EXIT_FAILURE);
    }

    signal(SIGTERM, sigterm_handler);

    if ((server_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Shell socket");
        exit(EXIT_FAILURE);
    }

    int opt = 1;
    setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(port);

    if (bind(server_socket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Shell bind");
        exit(EXIT_FAILURE);
    }

    if (listen(server_socket, MAX_CONNECTIONS) < 0) {
        perror("Shell listen");
        exit(EXIT_FAILURE);
    }

    printf("Shell server started on port %d (PID: %d)\n", port, getpid());

    while (!control_system.should_terminate) {
        client_socket = accept(server_socket, (struct sockaddr *)&client_addr, &client_len);
        if (client_socket < 0) {
            if (control_system.should_terminate) break;
            perror("Shell accept");
            continue;
        }

        printf("Shell client connected from %s:%d\n", 
               inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port));

        pid = fork();
        if (pid < 0) {
            perror("fork");
            close(client_socket);
        } else if (pid == 0) {
            close(server_socket);
            handle_shell_client(client_socket);
        } else {
            close(client_socket);
        }
    }

    close(server_socket);
    printf("Shell server shutdown complete\n");
    exit(EXIT_SUCCESS);
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <uid> [--Classjava-socket=class.method] [--Classjava-path=/path/to/apk] [--package=com.example.app] [--app-dir=/path/to/dir[:port]]\n", argv[0]);
        return EXIT_FAILURE;
    }
    
    // 检查第一个参数是否是数字（UID）
    int permission_level = 0;
    if (strspn(argv[1], "0123456789") == strlen(argv[1])) {
        permission_level = atoi(argv[1]);
        if (!check_permission((uid_t)permission_level)) {
            fprintf(stderr, "Error: Server must run with UID %d\n", permission_level);
            return EXIT_FAILURE;
        }
    } else {
        fprintf(stderr, "Error: First argument must be UID number\n");
        return EXIT_FAILURE;
    }

    memset(&control_system, 0, sizeof(control_system));
    control_system.current_pid = getpid();
    control_system.main_pid = getpid();
    control_system.app_dir_port = DEFAULT_APP_DIR_PORT; // 设置默认端口

    // 解析Java相关参数和app-dir参数
    parse_arguments(argc, argv);

    printf("Main process PID: %d\n", control_system.main_pid);
    printf("Java Class: %s\n", control_system.java_class);
    printf("Java Path: %s\n", control_system.java_path);
    printf("App Directory: %s\n", control_system.app_dir);
    printf("App Directory Port: %d\n", control_system.app_dir_port);

    // 如果有app-dir配置，启动app目录发送模式
    if (strlen(control_system.app_dir) > 0) {
        printf("Starting in app directory sending mode...\n");
        send_app_directory();
        return EXIT_SUCCESS;
    }

    // 如果有Java配置，启动Java应用模式
    if (strlen(control_system.java_class) > 0 && strlen(control_system.java_path) > 0) {
        printf("Starting in Java application mode...\n");
        start_java_application();
        return EXIT_SUCCESS;
    }

    // 否则启动正常的shell/control服务器模式
    pid_t shell_pid = fork();
    if (shell_pid == 0) {
        start_shell_server(8080);
        exit(EXIT_SUCCESS);
    } else {
        control_system.shell_server_pid = shell_pid;
        printf("Shell server started with PID: %d\n", shell_pid);
    }
    
    pid_t control_pid = fork();
    if (control_pid == 0) {
        start_control_server(8081);
        exit(EXIT_SUCCESS);
    } else {
        control_system.control_server_pid = control_pid;
        printf("Control server started with PID: %d\n", control_pid);
    }

    printf("启动远程终端成功 (主PID: %d)\n", getpid());
    
    signal(SIGTERM, sigterm_handler);
    
    int status;
    while (!control_system.should_terminate) {
        pid_t ended_pid = waitpid(-1, &status, WNOHANG);
        if (ended_pid > 0) {
            printf("子进程 %d 已结束\n", ended_pid);
        }
        sleep(1);
    }

    terminate_whole_system();

    printf("All servers shutdown complete\n");
    return EXIT_SUCCESS;
}