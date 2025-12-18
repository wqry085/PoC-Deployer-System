#ifndef SOCKET_SENDER_H
#define SOCKET_SENDER_H

#include <stdint.h>
#include <stdbool.h>



#define PROTOCOL_MAGIC      "ZFTP"
#define PROTOCOL_MAGIC_LEN  4
#define PROTOCOL_VERSION    0x0002  


#define TYPE_FILE           0x01
#define TYPE_DIRECTORY      0x02
#define TYPE_SYMLINK        0x03    
#define TYPE_END            0xFF    


#define CHECKSUM_NONE       0x00
#define CHECKSUM_CRC32      0x01


#define SEND_BUFFER_SIZE    (64 * 1024)  
#define PATH_MAX_LEN        4096




typedef struct __attribute__((packed)) {
    char     magic[4];          
    uint16_t version;           
    uint16_t flags;             
    uint32_t total_files;       
    uint32_t total_dirs;        
    uint64_t total_size;        
    uint8_t  checksum_type;     
    uint8_t  reserved[7];       
} ProtocolHeader;


typedef struct __attribute__((packed)) {
    uint8_t  type;              
    uint16_t path_length;       
    
    
    
    
} EntryHeader;



typedef enum {
    SEND_STATUS_SUCCESS = 0,
    SEND_STATUS_PORT_NOT_OPEN,
    SEND_STATUS_FOLDER_NOT_FOUND,
    SEND_STATUS_SOCKET_ERROR,
    SEND_STATUS_SEND_ERROR,
    SEND_STATUS_PROTOCOL_ERROR,
    SEND_STATUS_CANCELLED,
    SEND_STATUS_PERMISSION_DENIED,
    SEND_STATUS_OUT_OF_MEMORY
} SendStatus;




typedef void (*ProgressCallback)(
    const char* current_file,   
    uint32_t current_index,     
    uint32_t total_count,       
    uint64_t bytes_sent,        
    uint64_t total_bytes,       
    void* user_data             
);


typedef struct {
    int port;                       
    const char* host;               
    uint8_t checksum_type;          
    ProgressCallback progress_cb;   
    void* user_data;                
    volatile bool* cancel_flag;     
} SendOptions;




SendStatus send_folder(const char* folder_path, const SendOptions* options);


SendStatus send_folder_to_local_port(const char* folder_path, int port);


uint32_t calculate_crc32(const void* data, size_t length);

#endif 