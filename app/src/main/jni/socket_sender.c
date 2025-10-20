#include "socket_sender.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <errno.h> // 用于错误码
#include <endian.h> // 用于 htobe64
#include <android/log.h>

#define LOG_TAG "SocketSenderC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const uint8_t TYPE_FILE = 0x01;
const uint8_t TYPE_DIRECTORY = 0x02;

static bool is_port_open(int port);
static bool send_all(int sock, const void* data, size_t len);
static SendStatus send_directory_recursive(int sock, const char* base_path, const char* relative_path);


SendStatus send_folder_to_local_port(const char* folder_path, int port) {
    // 1. 检查端口
    if (!is_port_open(port)) {
        LOGE("Port %d is not open.", port);
        return SEND_STATUS_PORT_NOT_OPEN;
    }
    LOGI("Port %d is open. Proceeding to send folder.", port);

    // 2. 检查文件夹路径
    struct stat st;
    if (stat(folder_path, &st) != 0 || !S_ISDIR(st.st_mode)) {
        LOGE("Folder '%s' not found or is not a directory.", folder_path);
        return SEND_STATUS_FOLDER_NOT_FOUND;
    }

    // 3. 创建 Socket 并连接
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        LOGE("Socket creation failed: %s", strerror(errno));
        return SEND_STATUS_SOCKET_ERROR;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    server_addr.sin_addr.s_addr = inet_addr("127.0.0.1");

    if (connect(sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        LOGE("Failed to connect to 127.0.0.1:%d: %s", port, strerror(errno));
        close(sock);
        return SEND_STATUS_SOCKET_ERROR;
    }
    LOGI("Connected to port %d for data transfer. Starting recursive send.", port);

    // 4. 开始递归发送
    SendStatus status = send_directory_recursive(sock, folder_path, "");

    // 5. 关闭 Socket
    close(sock);

    if (status == SEND_STATUS_SUCCESS) {
        LOGI("Folder sending completed successfully.");
    } else {
        LOGE("An error occurred during folder sending.");
    }

    return status;
}

// --- 内部帮助函数实现 ---

static bool is_port_open(int port) {
    int check_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (check_sock < 0) return false;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");

    bool open = (connect(check_sock, (struct sockaddr*)&addr, sizeof(addr)) == 0);
    close(check_sock);
    return open;
}

static bool send_all(int sock, const void* data, size_t len) {
    const char* p = (const char*)data;
    while (len > 0) {
        ssize_t sent = send(sock, p, len, 0);
        if (sent <= 0) {
            LOGE("send() failed or connection closed: %s", strerror(errno));
            return false;
        }
        p += sent;
        len -= sent;
    }
    return true;
}

static SendStatus send_directory_recursive(int sock, const char* base_path, const char* relative_path) {
    char full_path[PATH_MAX];
    if (strlen(relative_path) == 0) {
        snprintf(full_path, PATH_MAX, "%s", base_path);
    } else {
        snprintf(full_path, PATH_MAX, "%s/%s", base_path, relative_path);
    }

    DIR* dir = opendir(full_path);
    if (!dir) {
        LOGE("Failed to open directory: %s, error: %s", full_path, strerror(errno));
        // 改为跳过无法打开的目录，而不是终止传输
        return SEND_STATUS_SUCCESS;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }

        // 构造新的相对路径和完整条目路径
        char new_relative_path[PATH_MAX];
        if (strlen(relative_path) == 0) {
            snprintf(new_relative_path, PATH_MAX, "%s", entry->d_name);
        } else {
            snprintf(new_relative_path, PATH_MAX, "%s/%s", relative_path, entry->d_name);
        }
        
        char entry_full_path[PATH_MAX];
        snprintf(entry_full_path, PATH_MAX, "%s/%s", base_path, new_relative_path);

        struct stat st;
        if (stat(entry_full_path, &st) != 0) {
            LOGE("Failed to stat: %s, error: %s", entry_full_path, strerror(errno));
            continue; // 跳过无法获取信息的条目
        }

        // 只处理普通文件和目录，跳过其他类型
        if (!S_ISREG(st.st_mode) && !S_ISDIR(st.st_mode)) {
            LOGI("Skipping non-regular file: %s", entry_full_path);
            continue;
        }

        // 准备协议头部
        size_t path_len = strlen(new_relative_path);
        if (path_len >= PATH_MAX) {
            LOGE("Path too long: %s", new_relative_path);
            continue; // 跳过路径过长的文件
        }
        
        uint32_t path_len_n = htonl(path_len);

        if (S_ISDIR(st.st_mode)) {
            LOGI("Sending directory entry: %s", new_relative_path);
            uint64_t data_len_n = 0; // 目录数据长度为0

            if (!send_all(sock, &TYPE_DIRECTORY, 1)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, &path_len_n, 4)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, new_relative_path, path_len)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, &data_len_n, 8)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }

            // 递归
            SendStatus recursive_status = send_directory_recursive(sock, base_path, new_relative_path);
            if (recursive_status != SEND_STATUS_SUCCESS) {
                closedir(dir);
                return recursive_status;
            }
        } else if (S_ISREG(st.st_mode)) {
            // 检查文件是否可读
            if (access(entry_full_path, R_OK) != 0) {
                LOGE("File not readable: %s, error: %s", entry_full_path, strerror(errno));
                continue; // 跳过不可读的文件
            }

            LOGI("Sending file entry: %s (size: %lld bytes)", new_relative_path, (long long)st.st_size);
            uint64_t file_size = st.st_size;
            uint64_t file_size_n = htobe64(file_size);

            if (!send_all(sock, &TYPE_FILE, 1)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, &path_len_n, 4)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, new_relative_path, path_len)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }
            if (!send_all(sock, &file_size_n, 8)) { 
                closedir(dir); 
                return SEND_STATUS_SEND_ERROR; 
            }

            // 发送文件内容
            FILE* file = fopen(entry_full_path, "rb");
            if (!file) {
                LOGE("Failed to open file: %s, error: %s", entry_full_path, strerror(errno));
                // 跳过无法打开的文件，而不是终止传输
                continue;
            }

            char buffer[4096];
            size_t bytes_read;
            bool file_send_success = true;
            
            while ((bytes_read = fread(buffer, 1, sizeof(buffer), file)) > 0) {
                if (!send_all(sock, buffer, bytes_read)) {
                    file_send_success = false;
                    break;
                }
            }
            fclose(file);
            
            if (!file_send_success) {
                closedir(dir);
                return SEND_STATUS_SEND_ERROR;
            }
        }
    }
    closedir(dir);
    return SEND_STATUS_SUCCESS;
}