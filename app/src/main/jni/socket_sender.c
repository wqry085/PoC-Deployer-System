#include "socket_sender.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <netinet/tcp.h>
#include <dirent.h>
#include <sys/stat.h>
#include <errno.h>
#include <endian.h>
#include <android/log.h>

#define LOG_TAG "SocketSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ==================== CRC32 实现 ====================

static const uint32_t crc32_table[256] = {
    0x00000000, 0x77073096, 0xEE0E612C, 0x990951BA, 0x076DC419, 0x706AF48F,
    0xE963A535, 0x9E6495A3, 0x0EDB8832, 0x79DCB8A4, 0xE0D5E91E, 0x97D2D988,
    0x09B64C2B, 0x7EB17CBD, 0xE7B82D07, 0x90BF1D91, 0x1DB71064, 0x6AB020F2,
    0xF3B97148, 0x84BE41DE, 0x1ADAD47D, 0x6DDDE4EB, 0xF4D4B551, 0x83D385C7,
    0x136C9856, 0x646BA8C0, 0xFD62F97A, 0x8A65C9EC, 0x14015C4F, 0x63066CD9,
    0xFA0F3D63, 0x8D080DF5, 0x3B6E20C8, 0x4C69105E, 0xD56041E4, 0xA2677172,
    0x3C03E4D1, 0x4B04D447, 0xD20D85FD, 0xA50AB56B, 0x35B5A8FA, 0x42B2986C,
    0xDBBBC9D6, 0xACBCF940, 0x32D86CE3, 0x45DF5C75, 0xDCD60DCF, 0xABD13D59,
    0x26D930AC, 0x51DE003A, 0xC8D75180, 0xBFD06116, 0x21B4F4B5, 0x56B3C423,
    0xCFBA9599, 0xB8BDA50F, 0x2802B89E, 0x5F058808, 0xC60CD9B2, 0xB10BE924,
    0x2F6F7C87, 0x58684C11, 0xC1611DAB, 0xB6662D3D, 0x76DC4190, 0x01DB7106,
    0x98D220BC, 0xEFD5102A, 0x71B18589, 0x06B6B51F, 0x9FBFE4A5, 0xE8B8D433,
    0x7807C9A2, 0x0F00F934, 0x9609A88E, 0xE10E9818, 0x7F6A0DBB, 0x086D3D2D,
    0x91646C97, 0xE6635C01, 0x6B6B51F4, 0x1C6C6162, 0x856530D8, 0xF262004E,
    0x6C0695ED, 0x1B01A57B, 0x8208F4C1, 0xF50FC457, 0x65B0D9C6, 0x12B7E950,
    0x8BBEB8EA, 0xFCB9887C, 0x62DD1DDF, 0x15DA2D49, 0x8CD37CF3, 0xFBD44C65,
    0x4DB26158, 0x3AB551CE, 0xA3BC0074, 0xD4BB30E2, 0x4ADFA541, 0x3DD895D7,
    0xA4D1C46D, 0xD3D6F4FB, 0x4369E96A, 0x346ED9FC, 0xAD678846, 0xDA60B8D0,
    0x44042D73, 0x33031DE5, 0xAA0A4C5F, 0xDD0D7CC9, 0x5005713C, 0x270241AA,
    0xBE0B1010, 0xC90C2086, 0x5768B525, 0x206F85B3, 0xB966D409, 0xCE61E49F,
    0x5EDEF90E, 0x29D9C998, 0xB0D09822, 0xC7D7A8B4, 0x59B33D17, 0x2EB40D81,
    0xB7BD5C3B, 0xC0BA6CAD, 0xEDB88320, 0x9ABFB3B6, 0x03B6E20C, 0x74B1D29A,
    0xEAD54739, 0x9DD277AF, 0x04DB2615, 0x73DC1683, 0xE3630B12, 0x94643B84,
    0x0D6D6A3E, 0x7A6A5AA8, 0xE40ECF0B, 0x9309FF9D, 0x0A00AE27, 0x7D079EB1,
    0xF00F9344, 0x8708A3D2, 0x1E01F268, 0x6906C2FE, 0xF762575D, 0x806567CB,
    0x196C3671, 0x6E6B06E7, 0xFED41B76, 0x89D32BE0, 0x10DA7A5A, 0x67DD4ACC,
    0xF9B9DF6F, 0x8EBEEFF9, 0x17B7BE43, 0x60B08ED5, 0xD6D6A3E8, 0xA1D1937E,
    0x38D8C2C4, 0x4FDFF252, 0xD1BB67F1, 0xA6BC5767, 0x3FB506DD, 0x48B2364B,
    0xD80D2BDA, 0xAF0A1B4C, 0x36034AF6, 0x41047A60, 0xDF60EFC3, 0xA867DF55,
    0x316E8EEF, 0x4669BE79, 0xCB61B38C, 0xBC66831A, 0x256FD2A0, 0x5268E236,
    0xCC0C7795, 0xBB0B4703, 0x220216B9, 0x5505262F, 0xC5BA3BBE, 0xB2BD0B28,
    0x2BB45A92, 0x5CB36A04, 0xC2D7FFA7, 0xB5D0CF31, 0x2CD99E8B, 0x5BDEAE1D,
    0x9B64C2B0, 0xEC63F226, 0x756AA39C, 0x026D930A, 0x9C0906A9, 0xEB0E363F,
    0x72076785, 0x05005713, 0x95BF4A82, 0xE2B87A14, 0x7BB12BAE, 0x0CB61B38,
    0x92D28E9B, 0xE5D5BE0D, 0x7CDCEFB7, 0x0BDBDF21, 0x86D3D2D4, 0xF1D4E242,
    0x68DDB3F8, 0x1FDA836E, 0x81BE16CD, 0xF6B9265B, 0x6FB077E1, 0x18B74777,
    0x88085AE6, 0xFF0F6A70, 0x66063BCA, 0x11010B5C, 0x8F659EFF, 0xF862AE69,
    0x616BFFD3, 0x166CCF45, 0xA00AE278, 0xD70DD2EE, 0x4E048354, 0x3903B3C2,
    0xA7672661, 0xD06016F7, 0x4969474D, 0x3E6E77DB, 0xAED16A4A, 0xD9D65ADC,
    0x40DF0B66, 0x37D83BF0, 0xA9BCAE53, 0xDEBB9EC5, 0x47B2CF7F, 0x30B5FFE9,
    0xBDBDF21C, 0xCABAC28A, 0x53B39330, 0x24B4A3A6, 0xBAD03605, 0xCDD706B3,
    0x54DE5729, 0x23D967BF, 0xB3667A2E, 0xC4614AB8, 0x5D681B02, 0x2A6F2B94,
    0xB40BBE37, 0xC30C8EA1, 0x5A05DF1B, 0x2D02EF8D
};

uint32_t calculate_crc32(const void* data, size_t length) {
    const uint8_t* buf = (const uint8_t*)data;
    uint32_t crc = 0xFFFFFFFF;
    
    for (size_t i = 0; i < length; i++) {
        crc = crc32_table[(crc ^ buf[i]) & 0xFF] ^ (crc >> 8);
    }
    
    return crc ^ 0xFFFFFFFF;
}

// 增量 CRC32
typedef struct {
    uint32_t crc;
} CRC32Context;

static void crc32_init(CRC32Context* ctx) {
    ctx->crc = 0xFFFFFFFF;
}

static void crc32_update(CRC32Context* ctx, const void* data, size_t length) {
    const uint8_t* buf = (const uint8_t*)data;
    for (size_t i = 0; i < length; i++) {
        ctx->crc = crc32_table[(ctx->crc ^ buf[i]) & 0xFF] ^ (ctx->crc >> 8);
    }
}

static uint32_t crc32_final(CRC32Context* ctx) {
    return ctx->crc ^ 0xFFFFFFFF;
}

// ==================== 传输上下文 ====================

typedef struct {
    int sock;
    const SendOptions* options;
    
    // 统计信息
    uint32_t total_files;
    uint32_t total_dirs;
    uint64_t total_size;
    uint32_t current_index;
    uint64_t bytes_sent;
    
    // 发送缓冲区
    uint8_t* send_buffer;
    size_t buffer_size;
    size_t buffer_used;
} TransferContext;

// ==================== 工具函数 ====================

static bool is_cancelled(const SendOptions* options) {
    return options && options->cancel_flag && *(options->cancel_flag);
}

static void report_progress(TransferContext* ctx, const char* current_file) {
    if (ctx->options && ctx->options->progress_cb) {
        ctx->options->progress_cb(
            current_file,
            ctx->current_index,
            ctx->total_files + ctx->total_dirs,
            ctx->bytes_sent,
            ctx->total_size,
            ctx->options->user_data
        );
    }
}

// 刷新发送缓冲区
static bool flush_buffer(TransferContext* ctx) {
    if (ctx->buffer_used == 0) return true;
    
    const uint8_t* p = ctx->send_buffer;
    size_t remaining = ctx->buffer_used;
    
    while (remaining > 0) {
        ssize_t sent = send(ctx->sock, p, remaining, MSG_NOSIGNAL);
        if (sent <= 0) {
            if (errno == EINTR) continue;
            LOGE("send() failed: %s", strerror(errno));
            return false;
        }
        p += sent;
        remaining -= sent;
        ctx->bytes_sent += sent;
    }
    
    ctx->buffer_used = 0;
    return true;
}

// 带缓冲的发送
static bool buffered_send(TransferContext* ctx, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    
    while (len > 0) {
        size_t space = ctx->buffer_size - ctx->buffer_used;
        size_t to_copy = (len < space) ? len : space;
        
        memcpy(ctx->send_buffer + ctx->buffer_used, p, to_copy);
        ctx->buffer_used += to_copy;
        p += to_copy;
        len -= to_copy;
        
        if (ctx->buffer_used >= ctx->buffer_size) {
            if (!flush_buffer(ctx)) return false;
        }
    }
    
    return true;
}

// 直接发送（用于大数据块）
static bool send_all(int sock, const void* data, size_t len, uint64_t* bytes_sent) {
    const uint8_t* p = (const uint8_t*)data;
    
    while (len > 0) {
        ssize_t sent = send(sock, p, len, MSG_NOSIGNAL);
        if (sent <= 0) {
            if (errno == EINTR) continue;
            LOGE("send() failed: %s", strerror(errno));
            return false;
        }
        p += sent;
        len -= sent;
        if (bytes_sent) *bytes_sent += sent;
    }
    
    return true;
}

// ==================== 扫描阶段 ====================

typedef struct {
    uint32_t file_count;
    uint32_t dir_count;
    uint64_t total_size;
} ScanResult;

static bool scan_directory(const char* base_path, const char* relative_path, 
                          ScanResult* result, const SendOptions* options) {
    if (is_cancelled(options)) return false;
    
    char full_path[PATH_MAX];
    if (strlen(relative_path) == 0) {
        snprintf(full_path, sizeof(full_path), "%s", base_path);
    } else {
        snprintf(full_path, sizeof(full_path), "%s/%s", base_path, relative_path);
    }
    
    DIR* dir = opendir(full_path);
    if (!dir) {
        LOGE("Cannot open directory: %s (%s)", full_path, strerror(errno));
        return true; // 跳过不可访问的目录
    }
    
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        
        char new_relative[PATH_MAX];
        if (strlen(relative_path) == 0) {
            snprintf(new_relative, sizeof(new_relative), "%s", entry->d_name);
        } else {
            snprintf(new_relative, sizeof(new_relative), "%s/%s", relative_path, entry->d_name);
        }
        
        char entry_path[PATH_MAX];
        snprintf(entry_path, sizeof(entry_path), "%s/%s", base_path, new_relative);
        
        struct stat st;
        if (lstat(entry_path, &st) != 0) {
            continue;
        }
        
        if (S_ISDIR(st.st_mode)) {
            result->dir_count++;
            scan_directory(base_path, new_relative, result, options);
        } else if (S_ISREG(st.st_mode)) {
            if (access(entry_path, R_OK) == 0) {
                result->file_count++;
                result->total_size += st.st_size;
            }
        }
    }
    
    closedir(dir);
    return true;
}

// ==================== 发送阶段 ====================

static SendStatus send_file_entry(TransferContext* ctx, const char* relative_path, 
                                  const char* full_path, uint64_t file_size) {
    // 条目头
    uint8_t type = TYPE_FILE;
    uint16_t path_len = htons((uint16_t)strlen(relative_path));
    uint64_t size_n = htobe64(file_size);
    
    if (!buffered_send(ctx, &type, 1)) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, &path_len, 2)) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, relative_path, strlen(relative_path))) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, &size_n, 8)) return SEND_STATUS_SEND_ERROR;
    
    // CRC32 (先发送占位符，稍后更新...实际上流式传输需要先计算)
    CRC32Context crc_ctx;
    uint32_t file_crc = 0;
    
    if (ctx->options->checksum_type == CHECKSUM_CRC32) {
        // 需要先读取文件计算 CRC
        crc32_init(&crc_ctx);
        FILE* f = fopen(full_path, "rb");
        if (f) {
            uint8_t buf[8192];
            size_t n;
            while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
                crc32_update(&crc_ctx, buf, n);
            }
            fclose(f);
            file_crc = crc32_final(&crc_ctx);
        }
        
        uint32_t crc_n = htonl(file_crc);
        if (!buffered_send(ctx, &crc_n, 4)) return SEND_STATUS_SEND_ERROR;
    }
    
    // 刷新缓冲区后发送文件内容
    if (!flush_buffer(ctx)) return SEND_STATUS_SEND_ERROR;
    
    // 发送文件内容
    FILE* file = fopen(full_path, "rb");
    if (!file) {
        LOGE("Cannot open file: %s (%s)", full_path, strerror(errno));
        // 发送0长度文件
        return SEND_STATUS_SUCCESS;
    }
    
    uint8_t buffer[SEND_BUFFER_SIZE];
    size_t bytes_read;
    
    while ((bytes_read = fread(buffer, 1, sizeof(buffer), file)) > 0) {
        if (is_cancelled(ctx->options)) {
            fclose(file);
            return SEND_STATUS_CANCELLED;
        }
        
        if (!send_all(ctx->sock, buffer, bytes_read, &ctx->bytes_sent)) {
            fclose(file);
            return SEND_STATUS_SEND_ERROR;
        }
        
        report_progress(ctx, relative_path);
    }
    
    fclose(file);
    ctx->current_index++;
    return SEND_STATUS_SUCCESS;
}

static SendStatus send_directory_entry(TransferContext* ctx, const char* relative_path) {
    uint8_t type = TYPE_DIRECTORY;
    uint16_t path_len = htons((uint16_t)strlen(relative_path));
    uint64_t size_n = 0;  // 目录大小为0
    
    if (!buffered_send(ctx, &type, 1)) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, &path_len, 2)) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, relative_path, strlen(relative_path))) return SEND_STATUS_SEND_ERROR;
    if (!buffered_send(ctx, &size_n, 8)) return SEND_STATUS_SEND_ERROR;
    
    if (ctx->options->checksum_type == CHECKSUM_CRC32) {
        uint32_t crc_n = 0;
        if (!buffered_send(ctx, &crc_n, 4)) return SEND_STATUS_SEND_ERROR;
    }
    
    ctx->current_index++;
    report_progress(ctx, relative_path);
    return SEND_STATUS_SUCCESS;
}

static SendStatus send_recursive(TransferContext* ctx, const char* base_path, 
                                 const char* relative_path) {
    if (is_cancelled(ctx->options)) {
        return SEND_STATUS_CANCELLED;
    }
    
    char full_path[PATH_MAX];
    if (strlen(relative_path) == 0) {
        snprintf(full_path, sizeof(full_path), "%s", base_path);
    } else {
        snprintf(full_path, sizeof(full_path), "%s/%s", base_path, relative_path);
    }
    
    DIR* dir = opendir(full_path);
    if (!dir) {
        LOGE("Cannot open directory: %s", full_path);
        return SEND_STATUS_SUCCESS;  // 跳过
    }
    
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        
        if (is_cancelled(ctx->options)) {
            closedir(dir);
            return SEND_STATUS_CANCELLED;
        }
        
        char new_relative[PATH_MAX];
        if (strlen(relative_path) == 0) {
            snprintf(new_relative, sizeof(new_relative), "%s", entry->d_name);
        } else {
            snprintf(new_relative, sizeof(new_relative), "%s/%s", relative_path, entry->d_name);
        }
        
        char entry_path[PATH_MAX];
        snprintf(entry_path, sizeof(entry_path), "%s/%s", base_path, new_relative);
        
        struct stat st;
        if (lstat(entry_path, &st) != 0) {
            continue;
        }
        
        SendStatus status;
        
        if (S_ISDIR(st.st_mode)) {
            LOGD("Sending directory: %s", new_relative);
            status = send_directory_entry(ctx, new_relative);
            if (status != SEND_STATUS_SUCCESS) {
                closedir(dir);
                return status;
            }
            
            status = send_recursive(ctx, base_path, new_relative);
            if (status != SEND_STATUS_SUCCESS) {
                closedir(dir);
                return status;
            }
        } else if (S_ISREG(st.st_mode)) {
            if (access(entry_path, R_OK) != 0) {
                LOGE("File not readable: %s", entry_path);
                continue;
            }
            
            LOGD("Sending file: %s (%lld bytes)", new_relative, (long long)st.st_size);
            status = send_file_entry(ctx, new_relative, entry_path, st.st_size);
            if (status != SEND_STATUS_SUCCESS) {
                closedir(dir);
                return status;
            }
        }
    }
    
    closedir(dir);
    return SEND_STATUS_SUCCESS;
}

// ==================== 主函数 ====================

SendStatus send_folder(const char* folder_path, const SendOptions* options) {
    if (!folder_path || !options) {
        return SEND_STATUS_FOLDER_NOT_FOUND;
    }
    
    // 检查文件夹
    struct stat st;
    if (stat(folder_path, &st) != 0 || !S_ISDIR(st.st_mode)) {
        LOGE("Invalid folder: %s", folder_path);
        return SEND_STATUS_FOLDER_NOT_FOUND;
    }
    
    const char* host = options->host ? options->host : "127.0.0.1";
    
    // 扫描阶段
    LOGI("Scanning folder: %s", folder_path);
    ScanResult scan = {0, 0, 0};
    scan_directory(folder_path, "", &scan, options);
    LOGI("Scan complete: %u files, %u dirs, %llu bytes", 
         scan.file_count, scan.dir_count, (unsigned long long)scan.total_size);
    
    if (is_cancelled(options)) {
        return SEND_STATUS_CANCELLED;
    }
    
    // 创建连接
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        LOGE("Socket creation failed: %s", strerror(errno));
        return SEND_STATUS_SOCKET_ERROR;
    }
    
    // 设置 TCP_NODELAY 减少延迟
    int flag = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    // 设置发送缓冲区
    int sndbuf = 256 * 1024;
    setsockopt(sock, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));
    
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(options->port);
    
    if (inet_pton(AF_INET, host, &server_addr.sin_addr) <= 0) {
        LOGE("Invalid address: %s", host);
        close(sock);
        return SEND_STATUS_SOCKET_ERROR;
    }
    
    if (connect(sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        LOGE("Connect failed: %s", strerror(errno));
        close(sock);
        return SEND_STATUS_PORT_NOT_OPEN;
    }
    
    LOGI("Connected to %s:%d", host, options->port);
    
    // 初始化上下文
    TransferContext ctx = {0};
    ctx.sock = sock;
    ctx.options = options;
    ctx.total_files = scan.file_count;
    ctx.total_dirs = scan.dir_count;
    ctx.total_size = scan.total_size;
    
    ctx.buffer_size = SEND_BUFFER_SIZE;
    ctx.send_buffer = (uint8_t*)malloc(ctx.buffer_size);
    if (!ctx.send_buffer) {
        close(sock);
        return SEND_STATUS_OUT_OF_MEMORY;
    }
    
    SendStatus status = SEND_STATUS_SUCCESS;
    
    // 发送协议头
    ProtocolHeader header;
    memcpy(header.magic, PROTOCOL_MAGIC, 4);
    header.version = htons(PROTOCOL_VERSION);
    header.flags = 0;
    header.total_files = htonl(scan.file_count);
    header.total_dirs = htonl(scan.dir_count);
    header.total_size = htobe64(scan.total_size);
    header.checksum_type = options->checksum_type;
    memset(header.reserved, 0, sizeof(header.reserved));
    
    if (!buffered_send(&ctx, &header, sizeof(header))) {
        status = SEND_STATUS_SEND_ERROR;
        goto cleanup;
    }
    
    LOGI("Protocol header sent");
    
    // 递归发送
    status = send_recursive(&ctx, folder_path, "");
    if (status != SEND_STATUS_SUCCESS) {
        goto cleanup;
    }
    
    // 发送结束标志
    uint8_t end_marker = TYPE_END;
    if (!buffered_send(&ctx, &end_marker, 1)) {
        status = SEND_STATUS_SEND_ERROR;
        goto cleanup;
    }
    
    // 刷新剩余数据
    if (!flush_buffer(&ctx)) {
        status = SEND_STATUS_SEND_ERROR;
        goto cleanup;
    }
    
    LOGI("Transfer complete: %llu bytes sent", (unsigned long long)ctx.bytes_sent);
    
cleanup:
    free(ctx.send_buffer);
    close(sock);
    return status;
}

// 兼容旧接口
SendStatus send_folder_to_local_port(const char* folder_path, int port) {
    SendOptions options = {
        .port = port,
        .host = "127.0.0.1",
        .checksum_type = CHECKSUM_CRC32,
        .progress_cb = NULL,
        .user_data = NULL,
        .cancel_flag = NULL
    };
    return send_folder(folder_path, &options);
}