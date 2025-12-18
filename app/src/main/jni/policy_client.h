/* policy_client.h */
#ifndef POLICY_CLIENT_H
#define POLICY_CLIENT_H

#include <sys/types.h>
#include <stdint.h>

#define POLICY_PORT 8083

/* 发送命令，返回 1=成功 */
int policy_cmd(const char *cmd, char *resp, size_t size);

/* 检查连接是否允许 (Shell Server 调用) */
int policy_check_conn(uint16_t local_port, const char *remote_ip, uint16_t remote_port);

/* 管理接口 */
int policy_add(uid_t uid);
int policy_remove(uid_t uid);
int policy_is_enabled(void);
int policy_alive(void);

#endif