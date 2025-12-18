#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/types.h>
#include <pwd.h>
#include <grp.h>
#include "app_process_launcher.h"
#include "socket_sender.h"
#include <arpa/inet.h>
#include <termios.h>
#include <signal.h>
#include <pty.h>
#include <utmp.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/epoll.h>
#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <openssl/md5.h>
#include "policy_client.h"



#define SHELL_PORT          8080
#define CONTROL_PORT        8081
#define DEFAULT_APP_DIR_PORT 8082
#define MAX_CONNECTIONS     64
#define BUFFER_SIZE         16384
#define SMALL_BUFFER        256
#define MAX_HISTORY         1000
#define AUTH_TIMEOUT        3
#define MAX_EPOLL_EVENTS    64
#define THREAD_POOL_SIZE    4



typedef struct {
    char command_history[MAX_HISTORY][BUFFER_SIZE];
    int history_count;
    pid_t current_pid;
    pid_t shell_server_pid;
    pid_t control_server_pid;
    pid_t main_pid;
    volatile int should_terminate;
    
    
    char java_class[BUFFER_SIZE];
    char java_path[BUFFER_SIZE];
    
    
    char app_dir[BUFFER_SIZE];
    int app_dir_port;
    
    pthread_mutex_t history_mutex;
} ControlSystem;

typedef struct TaskNode {
    int client_socket;
    struct TaskNode *next;
} TaskNode;

typedef struct {
    TaskNode *head;
    TaskNode *tail;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    int shutdown;
} TaskQueue;

typedef struct {
    pthread_t threads[THREAD_POOL_SIZE];
    TaskQueue queue;
} ThreadPool;



static ControlSystem g_control = {0};
static ThreadPool *g_thread_pool = NULL;



static inline void set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags != -1) {
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    }
}

static inline void set_socket_options(int sock) {
    int opt = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE, &opt, sizeof(opt));
    
    int bufsize = 65536;
    setsockopt(sock, SOL_SOCKET, SO_SNDBUF, &bufsize, sizeof(bufsize));
    setsockopt(sock, SOL_SOCKET, SO_RCVBUF, &bufsize, sizeof(bufsize));
}

static inline void set_socket_timeout(int sock, int seconds) {
    struct timeval tv = { .tv_sec = seconds, .tv_usec = 0 };
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
}

static inline ssize_t safe_send(int sock, const void *buf, size_t len) {
    ssize_t total = 0;
    while (total < (ssize_t)len) {
        ssize_t sent = send(sock, (const char*)buf + total, len - total, MSG_NOSIGNAL);
        if (sent <= 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                usleep(100);
                continue;
            }
            return -1;
        }
        total += sent;
    }
    return total;
}



static void generate_control_key(char *output, size_t output_size) {
    static const char seed[] = "wqry085";
    unsigned char md5_hash[MD5_DIGEST_LENGTH];
    MD5((unsigned char*)seed, sizeof(seed) - 1, md5_hash);
    
    for (int i = 0; i < MD5_DIGEST_LENGTH && (size_t)(i * 2 + 2) < output_size; i++) {
        snprintf(output + (i * 2), output_size - (i * 2), "%02x", md5_hash[i]);
    }
}

static int validate_control_key(const char *received_key) {
    char expected_key[33];
    generate_control_key(expected_key, sizeof(expected_key));
    return strcmp(received_key, expected_key) == 0;
}

static int authenticate_control_connection(int client_socket) {
    char buffer[64];
    
    set_socket_timeout(client_socket, AUTH_TIMEOUT);
    
    ssize_t bytes = recv(client_socket, buffer, sizeof(buffer) - 1, 0);
    if (bytes <= 0) return 0;
    
    buffer[bytes] = '\0';
    buffer[strcspn(buffer, "\r\n")] = '\0';
    
    if (!validate_control_key(buffer)) return 0;
    
    set_socket_timeout(client_socket, 0);
    return 1;
}



static int check_shell_permission(int client_socket) {
    struct sockaddr_in local_addr, peer_addr;
    socklen_t len;
    
    len = sizeof(local_addr);
    if (getsockname(client_socket, (struct sockaddr*)&local_addr, &len) < 0) {
        perror("getsockname");
        return 0;
    }
    
    len = sizeof(peer_addr);
    if (getpeername(client_socket, (struct sockaddr*)&peer_addr, &len) < 0) {
        perror("getpeername");
        return 0;
    }
    
    
    if (peer_addr.sin_addr.s_addr != htonl(INADDR_LOOPBACK)) {
        const char *msg = "Error: Remote connections not allowed.\r\n";
        safe_send(client_socket, msg, strlen(msg));
        return 0;
    }
    
    uint16_t local_port = ntohs(local_addr.sin_port);
    uint16_t remote_port = ntohs(peer_addr.sin_port);
    char remote_ip[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &peer_addr.sin_addr, remote_ip, sizeof(remote_ip));
    
    printf("Checking permission: local_port=%u, remote=%s:%u\n",
           local_port, remote_ip, remote_port);
    
    
    int result = policy_check_conn(local_port, remote_ip, remote_port);
    
    if (result == 1) {
        printf("Permission granted\n");
        return 1;
    }
    else if (result == 0) {
        printf("Permission denied\n");
        const char *msg = "Access denied: UID not in whitelist.\r\n";
        safe_send(client_socket, msg, strlen(msg));
        return 0;
    }
    else {
        const char *msg = "Exception: The server is unable to obtain the return information from the policy side. The policy side may not have been started. For security reasons, we have rejected your request\r\n";
        safe_send(client_socket, msg, strlen(msg));
        return 0;
    }
}



static void sigchld_handler(int sig) {
    (void)sig;
    while (waitpid(-1, NULL, WNOHANG) > 0);
}

static void sigterm_handler(int sig) {
    (void)sig;
    g_control.should_terminate = 1;
}



static void task_queue_init(TaskQueue *q) {
    q->head = q->tail = NULL;
    pthread_mutex_init(&q->mutex, NULL);
    pthread_cond_init(&q->cond, NULL);
    q->shutdown = 0;
}

static void task_queue_push(TaskQueue *q, int socket) {
    TaskNode *node = malloc(sizeof(TaskNode));
    if (!node) return;
    
    node->client_socket = socket;
    node->next = NULL;
    
    pthread_mutex_lock(&q->mutex);
    if (q->tail) {
        q->tail->next = node;
    } else {
        q->head = node;
    }
    q->tail = node;
    pthread_cond_signal(&q->cond);
    pthread_mutex_unlock(&q->mutex);
}

static int task_queue_pop(TaskQueue *q) {
    pthread_mutex_lock(&q->mutex);
    
    while (!q->head && !q->shutdown) {
        pthread_cond_wait(&q->cond, &q->mutex);
    }
    
    if (q->shutdown) {
        pthread_mutex_unlock(&q->mutex);
        return -1;
    }
    
    TaskNode *node = q->head;
    int socket = node->client_socket;
    q->head = node->next;
    if (!q->head) q->tail = NULL;
    
    pthread_mutex_unlock(&q->mutex);
    free(node);
    return socket;
}



static void add_to_history(const char *command) {
    if (!command || strlen(command) == 0) return;
    
    pthread_mutex_lock(&g_control.history_mutex);
    
    if (g_control.history_count < MAX_HISTORY) {
        strncpy(g_control.command_history[g_control.history_count],
                command, BUFFER_SIZE - 1);
        g_control.command_history[g_control.history_count][BUFFER_SIZE - 1] = '\0';
        g_control.history_count++;
    } else {
        memmove(&g_control.command_history[0],
                &g_control.command_history[1],
                (MAX_HISTORY - 1) * BUFFER_SIZE);
        strncpy(g_control.command_history[MAX_HISTORY - 1],
                command, BUFFER_SIZE - 1);
        g_control.command_history[MAX_HISTORY - 1][BUFFER_SIZE - 1] = '\0';
    }
    
    pthread_mutex_unlock(&g_control.history_mutex);
}



static void kill_all_processes(void) {
    printf("Killing all processes...\n");
    
    if (g_control.shell_server_pid > 0) {
        kill(g_control.shell_server_pid, SIGKILL);
    }
    if (g_control.control_server_pid > 0) {
        kill(g_control.control_server_pid, SIGKILL);
    }
    
    system("pkill -KILL -f zygote_term 2>/dev/null");
}

static void terminate_system(void) {
    printf("Terminating system...\n");
    
    if (g_thread_pool) {
        pthread_mutex_lock(&g_thread_pool->queue.mutex);
        g_thread_pool->queue.shutdown = 1;
        pthread_cond_broadcast(&g_thread_pool->queue.cond);
        pthread_mutex_unlock(&g_thread_pool->queue.mutex);
    }
    
    kill_all_processes();
    exit(EXIT_SUCCESS);
}



static int get_app_path(const char *package_name, char *path_buffer, size_t buffer_size) {
    char command[512];
    snprintf(command, sizeof(command), "pm path %s 2>/dev/null", package_name);
    
    FILE *fp = popen(command, "r");
    if (!fp) return 0;
    
    char buffer[512];
    int found = 0;
    if (fgets(buffer, sizeof(buffer), fp)) {
        char *start = strstr(buffer, "package:");
        if (start) {
            start += 8;
            char *end = strchr(start, '\n');
            if (end) *end = '\0';
            strncpy(path_buffer, start, buffer_size - 1);
            path_buffer[buffer_size - 1] = '\0';
            found = 1;
        }
    }
    
    pclose(fp);
    return found;
}

static void start_java_application(void) {
    if (strlen(g_control.java_class) == 0) {
        printf("Error: Java class not specified\n");
        return;
    }
    
    printf("Starting Java application: %s\n", g_control.java_class);
    printf("Java path: %s\n", g_control.java_path);
    
    char *args[] = { NULL };
    int pid = app_process_start("socket-java",
                                g_control.java_path,
                                g_control.java_class,
                                args);
    if (pid > 0) {
        int exit_code = app_process_wait(pid);
        printf("Java application exited with code: %d\n", exit_code);
    }
}

static void send_app_directory(void) {
    if (strlen(g_control.app_dir) == 0) {
        printf("Error: App directory not specified\n");
        return;
    }
    
    printf("Sending app directory: %s to port %d\n", 
           g_control.app_dir, g_control.app_dir_port);
    
    SendStatus status = send_folder_to_local_port(g_control.app_dir,
                                                  g_control.app_dir_port);
    printf("Send status: %d\n", status);
}



static void setup_pty_environment(void) {
    struct passwd *pw = getpwuid(getuid());
    if (pw) {
        setenv("USER", pw->pw_name, 1);
        setenv("LOGNAME", pw->pw_name, 1);
        setenv("HOME", pw->pw_dir, 1);
        setenv("SHELL", pw->pw_shell ? pw->pw_shell : "/system/bin/sh", 1);
    }
    setenv("TERM", "xterm-256color", 1);
    setenv("LANG", "en_US.UTF-8", 1);
    setenv("LC_ALL", "en_US.UTF-8", 1);
}

static void handle_shell_client(int client_socket) {
    int master_fd;
    pid_t pid;
    
    set_socket_options(client_socket);
    
    struct winsize ws = {
        .ws_row = 24,
        .ws_col = 80,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };
    
    pid = forkpty(&master_fd, NULL, NULL, &ws);
    
    if (pid < 0) {
        perror("forkpty");
        close(client_socket);
        return;
    }
    
    if (pid == 0) {
        
        close(client_socket);
        setup_pty_environment();
        
        struct termios term;
        if (tcgetattr(STDIN_FILENO, &term) == 0) {
            term.c_lflag |= (ECHO | ISIG | ICANON);
            term.c_iflag |= (ICRNL | IXON);
            term.c_oflag |= OPOST;
            tcsetattr(STDIN_FILENO, TCSANOW, &term);
        }
        
        char *shell = getenv("SHELL");
        if (!shell) shell = "/system/bin/sh";
        
        execlp(shell, shell, "-i", NULL);
        _exit(127);
    }
    
    
    set_nonblocking(master_fd);
    set_nonblocking(client_socket);
    
    int epfd = epoll_create1(0);
    if (epfd < 0) {
        kill(pid, SIGTERM);
        close(master_fd);
        close(client_socket);
        return;
    }
    
    struct epoll_event ev;
    ev.events = EPOLLIN;
    
    ev.data.fd = master_fd;
    epoll_ctl(epfd, EPOLL_CTL_ADD, master_fd, &ev);
    
    ev.data.fd = client_socket;
    epoll_ctl(epfd, EPOLL_CTL_ADD, client_socket, &ev);
    
    char buffer[BUFFER_SIZE];
    struct epoll_event events[2];
    int active = 1;
    
    while (active) {
        int nfds = epoll_wait(epfd, events, 2, 1000);
        
        if (nfds < 0) {
            if (errno == EINTR) continue;
            break;
        }
        
        
        int status;
        if (waitpid(pid, &status, WNOHANG) > 0) {
            break;
        }
        
        for (int i = 0; i < nfds; i++) {
            int fd = events[i].data.fd;
            
            if (events[i].events & (EPOLLERR | EPOLLHUP)) {
                active = 0;
                break;
            }
            
            if (events[i].events & EPOLLIN) {
                ssize_t bytes = read(fd, buffer, sizeof(buffer));
                if (bytes <= 0) {
                    if (errno != EAGAIN && errno != EINTR) {
                        active = 0;
                        break;
                    }
                    continue;
                }
                
                int target = (fd == master_fd) ? client_socket : master_fd;
                
                
                if (fd == client_socket && bytes >= 9) {
                    unsigned char *ubuf = (unsigned char*)buffer;
                    for (ssize_t j = 0; j < bytes - 8; j++) {
                        if (ubuf[j] == 0xFF && ubuf[j+1] == 0xFA && ubuf[j+2] == 0x1F) {
                            struct winsize new_ws;
                            new_ws.ws_col = (ubuf[j+3] << 8) | ubuf[j+4];
                            new_ws.ws_row = (ubuf[j+5] << 8) | ubuf[j+6];
                            if (new_ws.ws_col > 0 && new_ws.ws_row > 0) {
                                ioctl(master_fd, TIOCSWINSZ, &new_ws);
                                kill(pid, SIGWINCH);
                            }
                        }
                    }
                }
                
                ssize_t written = 0;
                while (written < bytes) {
                    ssize_t w = write(target, buffer + written, bytes - written);
                    if (w <= 0) {
                        if (errno == EINTR) continue;
                        if (errno == EAGAIN) {
                            usleep(50);
                            continue;
                        }
                        active = 0;
                        break;
                    }
                    written += w;
                }
            }
        }
    }
    
    close(epfd);
    kill(pid, SIGTERM);
    waitpid(pid, NULL, 0);
    close(master_fd);
    close(client_socket);
}



static void execute_shell_command(int client_socket, const char *command) {
    if (!command || strlen(command) == 0) {
        safe_send(client_socket, "ERROR: Empty command\n", 21);
        return;
    }
    
    char safe_command[BUFFER_SIZE * 2];
    snprintf(safe_command, sizeof(safe_command),
             "LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 %s 2>&1", command);
    
    add_to_history(command);
    
    FILE *fp = popen(safe_command, "r");
    if (!fp) {
        safe_send(client_socket, "ERROR: Failed to execute\n", 25);
        return;
    }
    
    char buffer[BUFFER_SIZE];
    while (fgets(buffer, sizeof(buffer), fp)) {
        safe_send(client_socket, buffer, strlen(buffer));
    }
    
    safe_send(client_socket, "COMMAND_COMPLETED\n", 18);
    pclose(fp);
}

static void handle_control_command(int client_socket, const char *command) {
    char response[BUFFER_SIZE * 4];
    
    add_to_history(command);
    
    if (strncmp(command, "EXEC ", 5) == 0) {
        execute_shell_command(client_socket, command + 5);
        return;
    }
    
    if (strcmp(command, "GET_HISTORY") == 0) {
        response[0] = '\0';
        pthread_mutex_lock(&g_control.history_mutex);
        for (int i = 0; i < g_control.history_count; i++) {
            size_t len = strlen(response);
            snprintf(response + len, sizeof(response) - len,
                     "%d: %s\n", i + 1, g_control.command_history[i]);
        }
        pthread_mutex_unlock(&g_control.history_mutex);
        strcat(response, "END_OF_HISTORY\n");
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "TERMINATE") == 0) {
        safe_send(client_socket, "SYSTEM_TERMINATING\n", 19);
        usleep(10000);
        terminate_system();
    }
    else if (strcmp(command, "KILL_ALL") == 0) {
        safe_send(client_socket, "KILLING_ALL\n", 12);
        kill_all_processes();
    }
    else if (strcmp(command, "START_JAVA") == 0) {
        safe_send(client_socket, "STARTING_JAVA\n", 14);
        start_java_application();
        safe_send(client_socket, "JAVA_COMPLETED\n", 15);
    }
    else if (strcmp(command, "SEND_APP_DIR") == 0) {
        safe_send(client_socket, "SENDING_APP_DIR\n", 16);
        send_app_directory();
        safe_send(client_socket, "SEND_COMPLETED\n", 15);
    }
    else if (strcmp(command, "STATUS") == 0) {
        snprintf(response, sizeof(response),
                 "Status: RUNNING\n"
                 "Main PID: %d\n"
                 "Shell Server PID: %d\n"
                 "Control Server PID: %d\n"
                 "UID: %d\n"
                 "History count: %d\n"
                 "Java class: %s\n"
                 "Java path: %s\n"
                 "App dir: %s\n"
                 "App dir port: %d\n"
                 "Policy daemon: %s\n"
                 "END_OF_STATUS\n",
                 g_control.main_pid,
                 g_control.shell_server_pid,
                 g_control.control_server_pid,
                 getuid(),
                 g_control.history_count,
                 strlen(g_control.java_class) ? g_control.java_class : "(not set)",
                 strlen(g_control.java_path) ? g_control.java_path : "(not set)",
                 strlen(g_control.app_dir) ? g_control.app_dir : "(not set)",
                 g_control.app_dir_port,
                 policy_alive() ? "running" : "not running");
        safe_send(client_socket, response, strlen(response));
    }
    else if (strncmp(command, "POLICY_ADD ", 11) == 0) {
        uid_t uid = (uid_t)atoi(command + 11);
        if (policy_add(uid)) {
            snprintf(response, sizeof(response), "OK: Added UID %u\n", uid);
        } else {
            snprintf(response, sizeof(response), "FAIL: Cannot add UID %u\n", uid);
        }
        safe_send(client_socket, response, strlen(response));
    }
    else if (strncmp(command, "POLICY_REMOVE ", 14) == 0) {
        uid_t uid = (uid_t)atoi(command + 14);
        if (policy_remove(uid)) {
            snprintf(response, sizeof(response), "OK: Removed UID %u\n", uid);
        } else {
            snprintf(response, sizeof(response), "FAIL: Cannot remove UID %u\n", uid);
        }
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "POLICY_LIST") == 0) {
        char list_resp[512];
        if (policy_cmd("LIST", list_resp, sizeof(list_resp))) {
            snprintf(response, sizeof(response), "Whitelist: %s\n", list_resp);
        } else {
            strcpy(response, "ERROR: Cannot get policy list\n");
        }
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "POLICY_STATUS") == 0) {
        char status_resp[64];
        if (policy_cmd("STATUS", status_resp, sizeof(status_resp))) {
            snprintf(response, sizeof(response), "Policy: %s\n", status_resp);
        } else {
            strcpy(response, "ERROR: Cannot get policy status\n");
        }
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "POLICY_ENABLE") == 0) {
        char r[32];
        policy_cmd("ENABLE", r, sizeof(r));
        snprintf(response, sizeof(response), "Policy: %s\n", r);
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "POLICY_DISABLE") == 0) {
        char r[32];
        policy_cmd("DISABLE", r, sizeof(r));
        snprintf(response, sizeof(response), "Policy: %s\n", r);
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "POLICY_SAVE") == 0) {
        char r[32];
        policy_cmd("SAVE", r, sizeof(r));
        snprintf(response, sizeof(response), "Policy save: %s\n", r);
        safe_send(client_socket, response, strlen(response));
    }
    else if (strcmp(command, "HELP") == 0) {
        strcpy(response,
               "Commands:\n"
               "  EXEC <cmd>          - Execute shell command\n"
               "  GET_HISTORY         - Get command history\n"
               "  STATUS              - Get system status\n"
               "  TERMINATE           - Terminate system\n"
               "  KILL_ALL            - Kill all processes\n"
               "  START_JAVA          - Start Java application\n"
               "  SEND_APP_DIR        - Send app directory\n"
               "  POLICY_ADD <uid>    - Add UID to whitelist\n"
               "  POLICY_REMOVE <uid> - Remove UID from whitelist\n"
               "  POLICY_LIST         - List whitelist\n"
               "  POLICY_STATUS       - Get policy status\n"
               "  POLICY_ENABLE       - Enable whitelist\n"
               "  POLICY_DISABLE      - Disable whitelist\n"
               "  POLICY_SAVE         - Save policy to file\n"
               "  EXIT                - Close connection\n"
               "  HELP                - Show this help\n"
               "END_OF_HELP\n");
        safe_send(client_socket, response, strlen(response));
    }
    else {
        safe_send(client_socket, "UNKNOWN_COMMAND\n", 16);
    }
    
    if (strcmp(command, "TERMINATE") != 0 && strcmp(command, "KILL_ALL") != 0) {
        safe_send(client_socket, "COMMAND_PROCESSED\n", 18);
    }
}



static void* thread_pool_worker(void *arg) {
    ThreadPool *pool = (ThreadPool*)arg;
    
    while (1) {
        int client_socket = task_queue_pop(&pool->queue);
        if (client_socket < 0) break;
        
        if (!authenticate_control_connection(client_socket)) {
            close(client_socket);
            continue;
        }
        
        safe_send(client_socket, "CONTROL_CONNECTED\n", 18);
        
        char buffer[BUFFER_SIZE];
        ssize_t bytes;
        
        while ((bytes = recv(client_socket, buffer, sizeof(buffer) - 1, 0)) > 0) {
            buffer[bytes] = '\0';
            buffer[strcspn(buffer, "\r\n")] = '\0';
            
            if (strcmp(buffer, "EXIT") == 0) {
                safe_send(client_socket, "CONTROL_EXIT_ACK\n", 17);
                break;
            }
            
            handle_control_command(client_socket, buffer);
        }
        
        close(client_socket);
    }
    
    return NULL;
}

static ThreadPool* thread_pool_create(void) {
    ThreadPool *pool = malloc(sizeof(ThreadPool));
    if (!pool) return NULL;
    
    task_queue_init(&pool->queue);
    
    for (int i = 0; i < THREAD_POOL_SIZE; i++) {
        pthread_create(&pool->threads[i], NULL, thread_pool_worker, pool);
    }
    
    return pool;
}

static void thread_pool_destroy(ThreadPool *pool) {
    if (!pool) return;
    
    pthread_mutex_lock(&pool->queue.mutex);
    pool->queue.shutdown = 1;
    pthread_cond_broadcast(&pool->queue.cond);
    pthread_mutex_unlock(&pool->queue.mutex);
    
    for (int i = 0; i < THREAD_POOL_SIZE; i++) {
        pthread_join(pool->threads[i], NULL);
    }
    
    pthread_mutex_destroy(&pool->queue.mutex);
    pthread_cond_destroy(&pool->queue.cond);
    free(pool);
}



static void print_usage(const char *prog) {
    printf("Usage: %s <uid> [options]\n", prog);
    printf("\nOptions:\n");
    printf("  --shell-port=PORT         Shell server port (default: %d)\n", SHELL_PORT);
    printf("  --control-port=PORT       Control server port (default: %d)\n", CONTROL_PORT);
    printf("  --Classjava-socket=CLASS  Java class to run\n");
    printf("  --Classjava-path=PATH     Java classpath\n");
    printf("  --package=PACKAGE         Get path from package name\n");
    printf("  --app-dir=DIR[:PORT]      Send directory to port\n");
    printf("\nExamples:\n");
    printf("  %s 2000\n", prog);
    printf("  %s 2000 --shell-port=9000\n", prog);
    printf("  %s 2000 --Classjava-socket=com.example.Main --package=com.example.app\n", prog);
    printf("  %s 2000 --app-dir=/data/app:8082\n", prog);
}

static void parse_arguments(int argc, char *argv[], int *shell_port, int *control_port) {
    *shell_port = SHELL_PORT;
    *control_port = CONTROL_PORT;
    g_control.app_dir_port = DEFAULT_APP_DIR_PORT;
    
    for (int i = 1; i < argc; i++) {
        if (strncmp(argv[i], "--shell-port=", 13) == 0) {
            *shell_port = atoi(argv[i] + 13);
        }
        else if (strncmp(argv[i], "--control-port=", 15) == 0) {
            *control_port = atoi(argv[i] + 15);
        }
        else if (strncmp(argv[i], "--Classjava-socket=", 19) == 0) {
            strncpy(g_control.java_class, argv[i] + 19, sizeof(g_control.java_class) - 1);
        }
        else if (strncmp(argv[i], "--Classjava-path=", 17) == 0) {
            strncpy(g_control.java_path, argv[i] + 17, sizeof(g_control.java_path) - 1);
        }
        else if (strncmp(argv[i], "--package=", 10) == 0) {
            get_app_path(argv[i] + 10, g_control.java_path, sizeof(g_control.java_path));
        }
        else if (strncmp(argv[i], "--app-dir=", 10) == 0) {
            char *arg = argv[i] + 10;
            char *port_sep = strchr(arg, ':');
            if (port_sep) {
                size_t len = port_sep - arg;
                if (len < sizeof(g_control.app_dir)) {
                    memcpy(g_control.app_dir, arg, len);
                    g_control.app_dir[len] = '\0';
                }
                g_control.app_dir_port = atoi(port_sep + 1);
            } else {
                strncpy(g_control.app_dir, arg, sizeof(g_control.app_dir) - 1);
            }
        }
        else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            print_usage(argv[0]);
            exit(0);
        }
    }
}



static void start_shell_server(int port) {
    int server_socket;
    struct sockaddr_in server_addr;
    
    struct sigaction sa;
    sa.sa_handler = sigchld_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART | SA_NOCLDSTOP;
    sigaction(SIGCHLD, &sa, NULL);
    signal(SIGTERM, sigterm_handler);
    signal(SIGPIPE, SIG_IGN);
    
    server_socket = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server_socket < 0) {
        perror("socket");
        exit(EXIT_FAILURE);
    }
    
    set_socket_options(server_socket);
    
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(port);
    
    if (bind(server_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        perror("bind");
        exit(EXIT_FAILURE);
    }
    
    if (listen(server_socket, MAX_CONNECTIONS) < 0) {
        perror("listen");
        exit(EXIT_FAILURE);
    }
    
    int epfd = epoll_create1(EPOLL_CLOEXEC);
    if (epfd < 0) {
        perror("epoll_create");
        exit(EXIT_FAILURE);
    }
    
    struct epoll_event ev;
    ev.events = EPOLLIN;
    ev.data.fd = server_socket;
    epoll_ctl(epfd, EPOLL_CTL_ADD, server_socket, &ev);
    
    printf("Shell server on port %d (PID: %d)\n", port, getpid());
    
    struct epoll_event events[MAX_EPOLL_EVENTS];
    
    while (!g_control.should_terminate) {
        int nfds = epoll_wait(epfd, events, MAX_EPOLL_EVENTS, 1000);
        
        if (nfds < 0) {
            if (errno == EINTR) continue;
            break;
        }
        
        for (int i = 0; i < nfds; i++) {
            if (events[i].data.fd == server_socket) {
                struct sockaddr_in client_addr;
                socklen_t client_len = sizeof(client_addr);
                
                int client_socket = accept4(server_socket,
                                            (struct sockaddr*)&client_addr,
                                            &client_len,
                                            SOCK_CLOEXEC);
                if (client_socket < 0) continue;
                
                printf("Shell client: %s:%d\n",
                       inet_ntoa(client_addr.sin_addr),
                       ntohs(client_addr.sin_port));
                
                pid_t pid = fork();
                if (pid == 0) {
                    close(server_socket);
                    close(epfd);
                    
                    if (check_shell_permission(client_socket)) {
                        handle_shell_client(client_socket);
                    }
                    close(client_socket);
                    _exit(0);
                }
                close(client_socket);
            }
        }
    }
    
    close(epfd);
    close(server_socket);
    exit(EXIT_SUCCESS);
}

static void start_control_server(int port) {
    int server_socket;
    struct sockaddr_in server_addr;
    
    signal(SIGTERM, sigterm_handler);
    signal(SIGPIPE, SIG_IGN);
    
    server_socket = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server_socket < 0) {
        perror("socket");
        exit(EXIT_FAILURE);
    }
    
    set_socket_options(server_socket);
    
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    server_addr.sin_port = htons(port);
    
    if (bind(server_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        perror("bind");
        exit(EXIT_FAILURE);
    }
    
    if (listen(server_socket, MAX_CONNECTIONS) < 0) {
        perror("listen");
        exit(EXIT_FAILURE);
    }
    
    g_thread_pool = thread_pool_create();
    if (!g_thread_pool) {
        perror("thread pool");
        exit(EXIT_FAILURE);
    }
    
    int epfd = epoll_create1(EPOLL_CLOEXEC);
    struct epoll_event ev;
    ev.events = EPOLLIN;
    ev.data.fd = server_socket;
    epoll_ctl(epfd, EPOLL_CTL_ADD, server_socket, &ev);
    
    printf("Control server on 127.0.0.1:%d (PID: %d)\n", port, getpid());
    
    struct epoll_event events[MAX_EPOLL_EVENTS];
    
    while (!g_control.should_terminate) {
        int nfds = epoll_wait(epfd, events, MAX_EPOLL_EVENTS, 1000);
        
        if (nfds < 0) {
            if (errno == EINTR) continue;
            break;
        }
        
        for (int i = 0; i < nfds; i++) {
            if (events[i].data.fd == server_socket) {
                struct sockaddr_in client_addr;
                socklen_t client_len = sizeof(client_addr);
                
                int client_socket = accept4(server_socket,
                                            (struct sockaddr*)&client_addr,
                                            &client_len,
                                            SOCK_CLOEXEC);
                if (client_socket < 0) continue;
                
                set_socket_options(client_socket);
                
                printf("Control client: %s:%d\n",
                       inet_ntoa(client_addr.sin_addr),
                       ntohs(client_addr.sin_port));
                
                task_queue_push(&g_thread_pool->queue, client_socket);
            }
        }
    }
    
    close(epfd);
    close(server_socket);
    thread_pool_destroy(g_thread_pool);
    exit(EXIT_SUCCESS);
}



int main(int argc, char *argv[]) {
    if (argc < 2) {
        print_usage(argv[0]);
        return EXIT_FAILURE;
    }
    
    
    int required_uid = -1;
    if (argv[1][0] >= '0' && argv[1][0] <= '9') {
        required_uid = atoi(argv[1]);
    }
    
    if (required_uid >= 0 && getuid() != (uid_t)required_uid) {
        fprintf(stderr, "Error: Must run with UID %d (current: %d)\n",
                required_uid, getuid());
        return EXIT_FAILURE;
    }
    
    
    memset(&g_control, 0, sizeof(g_control));
    pthread_mutex_init(&g_control.history_mutex, NULL);
    g_control.main_pid = getpid();
    g_control.app_dir_port = DEFAULT_APP_DIR_PORT;
    
    
    int shell_port, control_port;
    parse_arguments(argc, argv, &shell_port, &control_port);
    
    
    if (strlen(g_control.app_dir) > 0) {
        printf("Sending app directory...\n");
        send_app_directory();
        return EXIT_SUCCESS;
    }
    
    
    if (strlen(g_control.java_class) > 0 && strlen(g_control.java_path) > 0) {
        printf("Starting Java application...\n");
        start_java_application();
        return EXIT_SUCCESS;
    }
    
    
    printf("=== Zygote Terminal Server ===\n");
    printf("UID: %d\n", getuid());
    printf("Shell port: %d\n", shell_port);
    printf("Control port: %d\n", control_port);
    printf("Policy daemon: %s\n", policy_alive() ? "running" : "not running");
    printf("==============================\n");
    printf("Connect: stty raw -echo; nc 127.0.0.1 %d; stty sane\n", shell_port);
    printf("==============================\n");
    
    
    pid_t shell_pid = fork();
    if (shell_pid == 0) {
        start_shell_server(shell_port);
    }
    g_control.shell_server_pid = shell_pid;
    
    
    pid_t control_pid = fork();
    if (control_pid == 0) {
        start_control_server(control_port);
    }
    g_control.control_server_pid = control_pid;
    
    
    signal(SIGTERM, sigterm_handler);
    signal(SIGINT, sigterm_handler);
    
    while (!g_control.should_terminate) {
        int status;
        pid_t pid = waitpid(-1, &status, WNOHANG);
        
        if (pid > 0) {
            printf("Child %d exited with status %d\n", pid, WEXITSTATUS(status));
            
            
            if (pid == g_control.shell_server_pid && !g_control.should_terminate) {
                printf("Restarting shell server...\n");
                usleep(100000);
                shell_pid = fork();
                if (shell_pid == 0) {
                    start_shell_server(shell_port);
                }
                g_control.shell_server_pid = shell_pid;
            }
            else if (pid == g_control.control_server_pid && !g_control.should_terminate) {
                printf("Restarting control server...\n");
                usleep(100000);
                control_pid = fork();
                if (control_pid == 0) {
                    start_control_server(control_port);
                }
                g_control.control_server_pid = control_pid;
            }
        }
        
        sleep(1);
    }
    
    pthread_mutex_destroy(&g_control.history_mutex);
    terminate_system();
    return EXIT_SUCCESS;
}