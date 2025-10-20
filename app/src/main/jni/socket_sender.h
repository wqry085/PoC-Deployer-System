#ifndef SOCKET_SENDER_H
#define SOCKET_SENDER_H

#include <stdbool.h>

typedef enum {
    SEND_STATUS_SUCCESS,
    SEND_STATUS_PORT_NOT_OPEN,
    SEND_STATUS_FOLDER_NOT_FOUND,
    SEND_STATUS_SOCKET_ERROR,
    SEND_STATUS_SEND_ERROR,
    SEND_STATUS_PROTOCOL_ERROR,
    SEND_STATUS_MEMORY_ERROR
} SendStatus;

SendStatus send_folder_to_local_port(const char* folder_path, int port);

#endif // SOCKET_SENDER_H