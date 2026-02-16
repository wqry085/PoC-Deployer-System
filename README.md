# PoC-Deployer-System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)

[English](./README_EN.md) | 中文

基于 CVE-2024-31317 的 Zygote 注入工具制作的可视化工具，集成远程终端和文件传输。

[Binder-CLI](./binder.md) - 通过 binder 访问系统服务

## 截图

| UID/GID 注入 | 高级功能 | 反向 Shell |
|--------------|----------|------------|
| ![注入](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a1.jpg) | ![高级](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a2.jpg) | ![Shell](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a3.jpg) |

## 快速开始

### 环境要求
- Android 9-13，安全补丁 2024.6 之前
- 已激活 Shizuku
- 特殊设备需关闭厂商限制（MIUI/ColorOS/OriginOS）

### 使用步骤
1. 安装 Shizuku 并激活
2. 安装应用，授予 Shizuku 权限
3. 配置目标参数（UID/GID/SELinux上下文）
4. 启动远程终端（反弹shell）
5. 连接反向 Shell：
```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

## 功能

| 功能 | 说明 |
|------|------|
| Zygote 注入 | 通过 hidden_api_blacklist_exemptions 实现 |
| 远程终端 | 完整 PTY，支持窗口调整 |
| 应用数据传输 | 速度 50-100 MB/s |
| 访问控制 | UID 白名单 |

## 端口说明

| 端口 | 用途 | 认证 |
|------|------|------|
| 8080 | 本地反弹shell | 本地UID白名单 |
| 8081 | 控制接口 | MD5密钥 |
| 56423 | 文件接收 | 本地 |

## 8081控制命令
```
EXEC <cmd>       - 执行命令
STATUS           - 系统状态
POLICY_ADD <uid> - 添加白名单
POLICY_LIST      - 查看白名单
SEND_APP_DIR     - 发送应用目录
```

## 故障排查
```bash
# 检查进程
ps -A | grep zYg0te
# 检查端口
netstat -tlnp | grep 56423
# 查看日志
logcat -s FolderReceiver:*
```

## 免责声明
仅用于安全研究，禁止非法用途。使用者承担全部责任。

## 致谢
https://github.com/Webldix