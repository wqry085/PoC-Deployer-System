#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <signal.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/stat.h>

#define POLICY_PORT 8083
#define MAX_WHITELIST 256
#define BUFFER_SIZE 512
#define PERSIST_FILE "/data/local/tmp/policy_whitelist.dat"

typedef struct {
    uid_t whitelist[MAX_WHITELIST];
    int count;
    int enabled;
    pthread_rwlock_t lock;
    volatile int running;
} PolicyStore;

static PolicyStore g_store = {
    .count = 0,
    .enabled = 1,
    .running = 1
};

/* ========== 策略存储操作 ========== */

void policy_init(void) {
    pthread_rwlock_init(&g_store.lock, NULL);
    g_store.count = 0;
    g_store.enabled = 1;
    g_store.running = 1;
    g_store.whitelist[g_store.count++] = 0;
    g_store.whitelist[g_store.count++] = 2000;
}

int policy_check_uid(uid_t uid) {
    pthread_rwlock_rdlock(&g_store.lock);
    
    if (!g_store.enabled) {
        pthread_rwlock_unlock(&g_store.lock);
        return 1;
    }
    
    int found = 0;
    for (int i = 0; i < g_store.count; i++) {
        if (g_store.whitelist[i] == uid) {
            found = 1;
            break;
        }
    }
    
    pthread_rwlock_unlock(&g_store.lock);
    return found;
}

int policy_add(uid_t uid) {
    pthread_rwlock_wrlock(&g_store.lock);
    
    for (int i = 0; i < g_store.count; i++) {
        if (g_store.whitelist[i] == uid) {
            pthread_rwlock_unlock(&g_store.lock);
            return 1;
        }
    }
    
    if (g_store.count >= MAX_WHITELIST) {
        pthread_rwlock_unlock(&g_store.lock);
        return 0;
    }
    
    g_store.whitelist[g_store.count++] = uid;
    pthread_rwlock_unlock(&g_store.lock);
    return 1;
}

int policy_remove(uid_t uid) {
    if (uid == 0 || uid == 2000) return 0;
    
    pthread_rwlock_wrlock(&g_store.lock);
    
    int found = -1;
    for (int i = 0; i < g_store.count; i++) {
        if (g_store.whitelist[i] == uid) {
            found = i;
            break;
        }
    }
    
    if (found >= 0) {
        g_store.whitelist[found] = g_store.whitelist[--g_store.count];
    }
    
    pthread_rwlock_unlock(&g_store.lock);
    return found >= 0;
}

int policy_list(char *buf, size_t size) {
    pthread_rwlock_rdlock(&g_store.lock);
    
    buf[0] = '\0';
    size_t off = 0;
    
    for (int i = 0; i < g_store.count && off < size - 16; i++) {
        int w = snprintf(buf + off, size - off, "%s%u", i ? "," : "", g_store.whitelist[i]);
        if (w > 0) off += w;
    }
    
    if (g_store.count == 0) strcpy(buf, "(empty)");
    
    int c = g_store.count;
    pthread_rwlock_unlock(&g_store.lock);
    return c;
}

int policy_save(void) {
    pthread_rwlock_rdlock(&g_store.lock);
    
    FILE *fp = fopen(PERSIST_FILE, "w");
    if (!fp) {
        pthread_rwlock_unlock(&g_store.lock);
        return 0;
    }
    
    fprintf(fp, "enabled=%d\n", g_store.enabled);
    for (int i = 0; i < g_store.count; i++) {
        fprintf(fp, "%u\n", g_store.whitelist[i]);
    }
    
    fclose(fp);
    chmod(PERSIST_FILE, 0600);
    pthread_rwlock_unlock(&g_store.lock);
    return 1;
}

int policy_load(void) {
    FILE *fp = fopen(PERSIST_FILE, "r");
    if (!fp) return 0;
    
    pthread_rwlock_wrlock(&g_store.lock);
    
    char line[64];
    int new_enabled = 1;
    int new_count = 0;
    uid_t new_list[MAX_WHITELIST];
    
    while (fgets(line, sizeof(line), fp)) {
        line[strcspn(line, "\r\n")] = '\0';
        if (line[0] == '#' || line[0] == '\0') continue;
        
        if (strncmp(line, "enabled=", 8) == 0) {
            new_enabled = atoi(line + 8);
        } else if (line[0] >= '0' && line[0] <= '9') {
            if (new_count < MAX_WHITELIST) {
                new_list[new_count++] = (uid_t)strtoul(line, NULL, 10);
            }
        }
    }
    fclose(fp);
    
    int has_root = 0, has_shell = 0;
    for (int i = 0; i < new_count; i++) {
        if (new_list[i] == 0) has_root = 1;
        if (new_list[i] == 2000) has_shell = 1;
    }
    if (!has_root && new_count < MAX_WHITELIST) new_list[new_count++] = 0;
    if (!has_shell && new_count < MAX_WHITELIST) new_list[new_count++] = 2000;
    
    g_store.enabled = new_enabled;
    g_store.count = new_count;
    memcpy(g_store.whitelist, new_list, new_count * sizeof(uid_t));
    
    pthread_rwlock_unlock(&g_store.lock);
    return 1;
}

/* ========== 核心功能：从 /proc/net/tcp 查找客户端 UID ========== */

/**
 * 调试：打印 /proc/net/tcp 内容
 */
void debug_print_proc_net_tcp(void) {
    FILE *fp = fopen("/proc/net/tcp", "r");
    if (!fp) return;
    
    char line[512];
    printf("  === /proc/net/tcp ===\n");
    while (fgets(line, sizeof(line), fp)) {
        printf("  %s", line);
    }
    printf("  ======================\n");
    fclose(fp);
}

/**
 * 根据连接信息查找客户端进程的 UID
 * 
 * 原理：
 * - Shell Server (端口8080) 收到客户端连接，客户端使用临时端口 (如 37883)
 * - 在 /proc/net/tcp 中，客户端进程的条目是:
 *     local = 127.0.0.1:37883 (客户端端口)
 *     remote = 127.0.0.1:8080 (服务端端口)
 *     UID = 客户端进程的 UID (这是我们要的)
 * - 服务端进程的条目是:
 *     local = 0.0.0.0:8080 或 127.0.0.1:8080
 *     remote = 127.0.0.1:37883
 *     UID = 服务端进程的 UID (不是我们要的)
 */
int find_uid_by_connection(uint16_t server_port, uint32_t client_ip,
                           uint16_t client_port, uid_t *uid_out) {
    FILE *fp = fopen("/proc/net/tcp", "r");
    if (!fp) {
        perror("fopen /proc/net/tcp");
        return -1;
    }
    
    char line[512];
    int found = 0;
    
    /* 跳过标题行 */
    if (!fgets(line, sizeof(line), fp)) {
        fclose(fp);
        return -1;
    }
    
    /*
     * 我们要找客户端的条目:
     *   local_ip:local_port = 客户端地址 (client_ip:client_port)
     *   remote_port = 服务端端口 (server_port, 如 8080)
     * 
     * /proc/net/tcp 中 IP 地址格式是小端序十六进制
     * 例如 127.0.0.1 = 0x7F000001 存储为 0100007F
     */
    
    while (fgets(line, sizeof(line), fp)) {
        unsigned int slot;
        unsigned int l_ip, l_port;
        unsigned int r_ip, r_port;
        unsigned int state;
        unsigned int uid_val;
        
        int matched = sscanf(line,
            " %u: %X:%X %X:%X %X %*X:%*X %*X:%*X %*X %u",
            &slot, &l_ip, &l_port, &r_ip, &r_port, &state, &uid_val);
        
        if (matched >= 7) {
            /*
             * 匹配客户端条目:
             *   local = client_ip:client_port
             *   remote = *:server_port
             */
            if (l_ip == client_ip && 
                l_port == client_port &&
                r_port == server_port &&
                state == 1) {  /* ESTABLISHED */
                
                *uid_out = (uid_t)uid_val;
                found = 1;
                break;
            }
        }
    }
    
    fclose(fp);
    return found ? 0 : -1;
}

/**
 * 检查连接是否允许
 */
int check_connection(uint16_t server_port, const char *client_ip_str, uint16_t client_port) {
    struct in_addr addr;
    if (inet_aton(client_ip_str, &addr) == 0) {
        printf("  Invalid IP: %s\n", client_ip_str);
        return -1;
    }
    uint32_t client_ip = addr.s_addr;
    
    /* 调试输出 */
    printf("  Searching: client=%s:%u (0x%08X:%u) -> server_port=%u\n",
           client_ip_str, client_port, client_ip, client_port, server_port);
    
    /* 查找客户端 UID */
    uid_t uid;
    if (find_uid_by_connection(server_port, client_ip, client_port, &uid) != 0) {
        /* 调试：打印 /proc/net/tcp */
        printf("  Connection not found, dumping /proc/net/tcp:\n");
        debug_print_proc_net_tcp();
        return -1;
    }
    
    printf("  -> Client UID: %u\n", uid);
    
    /* 检查白名单 */
    int allowed = policy_check_uid(uid);
    printf("  -> Whitelist check: %s\n", allowed ? "ALLOW" : "DENY");
    
    return allowed;
}

/* ========== 网络处理 ========== */

void handle_client(int fd, struct sockaddr_in *addr) {
    char buf[BUFFER_SIZE];
    char resp[BUFFER_SIZE];
    
    struct timeval tv = { .tv_sec = 5, .tv_usec = 0 };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    
    ssize_t n = recv(fd, buf, sizeof(buf) - 1, 0);
    if (n <= 0) {
        close(fd);
        return;
    }
    
    buf[n] = '\0';
    buf[strcspn(buf, "\r\n")] = '\0';
    
    printf("[%s:%d] %s\n", inet_ntoa(addr->sin_addr), ntohs(addr->sin_port), buf);
    
    char *cmd = buf;
    char *arg = strchr(buf, ' ');
    if (arg) {
        *arg++ = '\0';
        while (*arg == ' ') arg++;
    }
    
    if (strcmp(cmd, "PING") == 0) {
        strcpy(resp, "PONG\n");
    }
    else if (strcmp(cmd, "CHECK_CONN") == 0 && arg) {
        unsigned int sport, cport;
        char cip[32];
        
        if (sscanf(arg, "%u %31s %u", &sport, cip, &cport) == 3) {
            int result = check_connection((uint16_t)sport, cip, (uint16_t)cport);
            if (result == 1) {
                strcpy(resp, "ALLOW\n");
            } else if (result == 0) {
                strcpy(resp, "DENY\n");
            } else {
                strcpy(resp, "ERROR:NOT_FOUND\n");
            }
        } else {
            strcpy(resp, "ERROR:BAD_FORMAT\n");
        }
    }
    else if (strcmp(cmd, "CHECK_UID") == 0 && arg) {
        uid_t uid = (uid_t)strtoul(arg, NULL, 10);
        strcpy(resp, policy_check_uid(uid) ? "ALLOW\n" : "DENY\n");
    }
    else if (strcmp(cmd, "ADD") == 0 && arg) {
        uid_t uid = (uid_t)strtoul(arg, NULL, 10);
        strcpy(resp, policy_add(uid) ? "OK\n" : "FAIL\n");
    }
    else if (strcmp(cmd, "REMOVE") == 0 && arg) {
        uid_t uid = (uid_t)strtoul(arg, NULL, 10);
        strcpy(resp, policy_remove(uid) ? "OK\n" : "FAIL\n");
    }
    else if (strcmp(cmd, "LIST") == 0) {
        char list[BUFFER_SIZE - 32];
        policy_list(list, sizeof(list));
        snprintf(resp, sizeof(resp), "%s\n", list);
    }
    else if (strcmp(cmd, "ENABLE") == 0) {
        pthread_rwlock_wrlock(&g_store.lock);
        g_store.enabled = 1;
        pthread_rwlock_unlock(&g_store.lock);
        strcpy(resp, "OK\n");
    }
    else if (strcmp(cmd, "DISABLE") == 0) {
        pthread_rwlock_wrlock(&g_store.lock);
        g_store.enabled = 0;
        pthread_rwlock_unlock(&g_store.lock);
        strcpy(resp, "OK\n");
    }
    else if (strcmp(cmd, "STATUS") == 0) {
        pthread_rwlock_rdlock(&g_store.lock);
        snprintf(resp, sizeof(resp), "%s,%d\n",
                 g_store.enabled ? "ENABLED" : "DISABLED",
                 g_store.count);
        pthread_rwlock_unlock(&g_store.lock);
    }
    else if (strcmp(cmd, "SAVE") == 0) {
        strcpy(resp, policy_save() ? "OK\n" : "FAIL\n");
    }
    else if (strcmp(cmd, "LOAD") == 0) {
        strcpy(resp, policy_load() ? "OK\n" : "FAIL\n");
    }
    else if (strcmp(cmd, "DEBUG") == 0) {
        /* 调试命令：打印 /proc/net/tcp */
        debug_print_proc_net_tcp();
        strcpy(resp, "OK\n");
    }
    else if (strcmp(cmd, "SHUTDOWN") == 0) {
        strcpy(resp, "BYE\n");
        send(fd, resp, strlen(resp), MSG_NOSIGNAL);
        close(fd);
        g_store.running = 0;
        return;
    }
    else {
        strcpy(resp, "ERROR:UNKNOWN\n");
    }
    
    send(fd, resp, strlen(resp), MSG_NOSIGNAL);
    close(fd);
}

void signal_handler(int sig) {
    (void)sig;
    g_store.running = 0;
}

int main(int argc, char *argv[]) {
    int daemon_mode = 0;
    int port = POLICY_PORT;
    
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-d") == 0) {
            daemon_mode = 1;
        } else if (strcmp(argv[i], "-p") == 0 && i + 1 < argc) {
            port = atoi(argv[++i]);
        }
    }
    
    if (daemon_mode) {
        pid_t pid = fork();
        if (pid < 0) { perror("fork"); return 1; }
        if (pid > 0) {
            printf("Policy daemon: PID=%d, PORT=%d\n", pid, port);
            return 0;
        }
        setsid();
        int null_fd = open("/dev/null", O_RDWR);
        if (null_fd >= 0) {
            dup2(null_fd, STDIN_FILENO);
            /* 保留 stdout/stderr 用于调试 */
            // dup2(null_fd, STDOUT_FILENO);
            // dup2(null_fd, STDERR_FILENO);
            if (null_fd > 2) close(null_fd);
        }
    }
    
    policy_init();
    policy_load();
    
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGPIPE, SIG_IGN);
    
    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) { perror("socket"); return 1; }
    
    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    setsockopt(server_fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
    
    struct sockaddr_in addr = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(port)
    };
    
    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        perror("bind"); return 1;
    }
    if (listen(server_fd, 32) < 0) {
        perror("listen"); return 1;
    }
    
    printf("Policy daemon on 127.0.0.1:%d (UID=%d)\n", port, getuid());
    printf("Commands: PING, CHECK_CONN, CHECK_UID, ADD, REMOVE, LIST, ENABLE, DISABLE, STATUS, SAVE, LOAD, DEBUG, SHUTDOWN\n");
    
    char list[256];
    policy_list(list, sizeof(list));
    printf("Whitelist: %s\n", list);
    
    while (g_store.running) {
        fd_set fds;
        FD_ZERO(&fds);
        FD_SET(server_fd, &fds);
        
        struct timeval tv = { .tv_sec = 1, .tv_usec = 0 };
        int ret = select(server_fd + 1, &fds, NULL, NULL, &tv);
        
        if (ret > 0 && FD_ISSET(server_fd, &fds)) {
            struct sockaddr_in client_addr;
            socklen_t len = sizeof(client_addr);
            int client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &len);
            if (client_fd >= 0) {
                if (client_addr.sin_addr.s_addr == htonl(INADDR_LOOPBACK)) {
                    handle_client(client_fd, &client_addr);
                } else {
                    close(client_fd);
                }
            }
        }
    }
    
    policy_save();
    close(server_fd);
    pthread_rwlock_destroy(&g_store.lock);
    return 0;
}