/* policy_client.c */
#include "policy_client.h"
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>

static int connect_daemon(void) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;
    
    int opt = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
    
    /* 非阻塞连接 */
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);
    
    struct sockaddr_in addr = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(POLICY_PORT)
    };
    
    int ret = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
    if (ret < 0 && errno != EINPROGRESS) {
        close(sock);
        return -1;
    }
    
    if (ret < 0) {
        fd_set wfds;
        FD_ZERO(&wfds);
        FD_SET(sock, &wfds);
        struct timeval tv = { .tv_sec = 0, .tv_usec = 100000 }; /* 100ms */
        
        if (select(sock + 1, NULL, &wfds, NULL, &tv) <= 0) {
            close(sock);
            return -1;
        }
        
        int err = 0;
        socklen_t len = sizeof(err);
        getsockopt(sock, SOL_SOCKET, SO_ERROR, &err, &len);
        if (err) {
            close(sock);
            return -1;
        }
    }
    
    fcntl(sock, F_SETFL, flags);
    
    struct timeval tv = { .tv_sec = 0, .tv_usec = 500000 };
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    
    return sock;
}

int policy_cmd(const char *cmd, char *resp, size_t size) {
    int sock = connect_daemon();
    if (sock < 0) return 0;
    
    send(sock, cmd, strlen(cmd), MSG_NOSIGNAL);
    
    ssize_t n = recv(sock, resp, size - 1, 0);
    close(sock);
    
    if (n <= 0) return 0;
    
    resp[n] = '\0';
    resp[strcspn(resp, "\r\n")] = '\0';
    return 1;
}

/**
 * 检查连接是否允许
 * Shell Server 调用此函数，传入自己的端口和客户端的 IP/端口
 * @return 1=允许, 0=拒绝, -1=错误/守护进程不可用
 */
int policy_check_conn(uint16_t local_port, const char *remote_ip, uint16_t remote_port) {
    char cmd[128];
    char resp[64];
    
    snprintf(cmd, sizeof(cmd), "CHECK_CONN %u %s %u", local_port, remote_ip, remote_port);
    
    if (!policy_cmd(cmd, resp, sizeof(resp))) {
        return -1;  /* 守护进程不可用 */
    }
    
    if (strcmp(resp, "ALLOW") == 0) return 1;
    if (strcmp(resp, "DENY") == 0) return 0;
    return -1;
}

int policy_add(uid_t uid) {
    char cmd[64], resp[32];
    snprintf(cmd, sizeof(cmd), "ADD %u", uid);
    return policy_cmd(cmd, resp, sizeof(resp)) && strcmp(resp, "OK") == 0;
}

int policy_remove(uid_t uid) {
    char cmd[64], resp[32];
    snprintf(cmd, sizeof(cmd), "REMOVE %u", uid);
    return policy_cmd(cmd, resp, sizeof(resp)) && strcmp(resp, "OK") == 0;
}

int policy_is_enabled(void) {
    char resp[64];
    if (!policy_cmd("STATUS", resp, sizeof(resp))) return -1;
    if (strncmp(resp, "ENABLED", 7) == 0) return 1;
    if (strncmp(resp, "DISABLED", 8) == 0) return 0;
    return -1;
}

int policy_alive(void) {
    char resp[32];
    return policy_cmd("PING", resp, sizeof(resp)) && strcmp(resp, "PONG") == 0;
}