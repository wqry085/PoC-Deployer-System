# PoC-Deployer-System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)
[![Shizuku](https://img.shields.io/badge/Shizuku-Required-purple.svg)](https://shizuku.rikka.app/)

一个基于 CVE-2024-31317 的 Android Zygote 注入工具，集成远程 Shell、文件传输、策略管理等功能。

## 项目声明

### 致谢
尽管 PoC-Deployer-System 已经是一个独立项目，但在此特别感谢原项目的启发和贡献：

**原项目**: https://github.com/Webldix/CVE-2024-31317-PoC-Deployer

---

## 更新内容

### v1.5.7 新特性
- **【远程终端】** 完整 PTY 终端，支持窗口大小调整
- **【应用数据提取】** ZFTP 协议，支持 CRC32 校验，传输速度 50-100 MB/s
- **【策略授权】** 基于 UID 白名单的访问控制确保只有被授权的uid才能访问远程终端
- **【控制接口】** 远程命令执行和状态监控
- **【ADB 集成】** 支持通过 ADB 直接调用 Payload

### v1.5.6 特性
- **【uid/gid/selinux 上下文/groups 自定义注入】**
- **【添加应用数据提取功能】**
- **【添加 Zygote 日志监听功能】**
- **【添加可使用 Zygote 参数】**

---

## 功能展示

### uid/gid/groups 注入功能
![应用图片](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a1.jpg)

### 高级功能界面
![应用图片2](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a2.jpg)

### 反向 Shell
![应用图片3](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a3.jpg)

---

## 系统要求

### 支持的环境

| 条件 | 要求 |
|------|------|
| 安全补丁 | 2024年6月之前 |
| Android 版本 | 9 - 14 |
| 权限要求 | Shizuku 权限 |
| 架构 | arm64-v8a / armeabi-v7a / x86 / x86_64 |

### 特殊设备说明

| 设备/系统 | 问题 | 解决方案 |
|-----------|------|----------|
| MIUI/澎湃 | Shell 缺少 `WRITE_SECURE_SETTINGS` 权限 | 开启「USB 调试（安全设置）」 |
| OPPO/ColorOS | 权限监控拦截 | 关闭「权限监控」功能 |
| Vivo/OriginOS | 类似权限问题 | 进入开发者选项关闭相关限制 |

---

## 主要特性

### 核心功能

| 功能 | 说明 |
|------|------|
| 🔧 **Zygote 注入** | 通过 hidden_api_blacklist_exemptions 实现进程注入 |
| 💻 **远程终端** | 完整 PTY 终端，支持 Telnet NAWS 协议 |
| 📁 **应用数据提取** | ZFTP 协议，64KB 缓冲区，CRC32 校验 |
| 🔐 **策略管理** | UID 白名单，持久化存储 |
| 🎛️ **控制接口** | 远程命令执行，状态监控 |
| 📱 **ADB 集成** | 静默模式直接调用 |

### 技术特点
- 只需 Shizuku 权限即可实现提权操作
- 多进程架构，自动重启崩溃服务
- 线程池处理并发连接
- epoll 高性能 I/O

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Android App                            │
│  ┌───────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ ZygoteFragment│  │  RunPayload │  │  FolderReceiver  │  │
│  │  (配置界面)   │  │ (Payload载入)│  │   (文件接收)     │  │
│  └───────┬───────┘  └──────┬──────┘  └────────┬─────────┘  │
│          └─────────────────┴──────────────────┘            │
│                            │ Shizuku                        │
└────────────────────────────┼────────────────────────────────┘
                             ▼
              ┌──────────────────────────────┐
              │  hidden_api_blacklist_       │
              │       exemptions             │
              └──────────────┬───────────────┘
                             ▼
              ┌──────────────────────────────┐
              │       Zygote Process         │
              │    ┌──────────────────┐      │
              │    │  zygote_term.so  │      │
              │    └────────┬─────────┘      │
              └─────────────┼────────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
┌─────────────────┐ ┌───────────────┐ ┌───────────────┐
│  Shell Server   │ │Control Server │ │ Policy Daemon │
│   Port 8080     │ │  Port 8081    │ │  (白名单管理)  │
└─────────────────┘ └───────────────┘ └───────────────┘
```

---

## 使用说明

### 快速开始

1. **安装 Shizuku** 并激活
2. **安装应用** 并授予 Shizuku 权限
3. **配置参数**：

| 参数 | 说明 | 示例值 |
|------|------|--------|
| UID | 目标用户 ID | `1000` (system) |
| GID | 目标组 ID | `1000` |
| SELinux | SELinux 上下文 | `platform:privapp:targetSdkVersion=29` |
| Nice Name | 进程名称 | `zYg0te` |
| Runtime Flags | 运行时标志 | `43267` |

4. **执行注入**
5. **连接反方向Shell**：

```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

### 端口说明

| 端口 | 用途 | 认证方式 |
|------|------|----------|
| 8080 | Shell 服务 | 本地连接 + UID 白名单 |
| 8081 | 控制接口 | MD5 密钥认证 |
| 56423 | 文件接收 | 无（仅本地） |

### 控制命令

连接控制接口后可用的命令：

```
EXEC <cmd>          - 执行 Shell 命令
STATUS              - 获取系统状态
GET_HISTORY         - 获取命令历史
TERMINATE           - 终止系统
POLICY_ADD <uid>    - 添加 UID 到白名单
POLICY_REMOVE <uid> - 从白名单移除 UID
POLICY_LIST         - 列出白名单
SEND_APP_DIR        - 发送应用目录
HELP                - 显示帮助
EXIT                - 断开连接
```

### ADB 直接调用

```bash
# 加载 Payload
adb shell am start -n com.wqry085.deployesystem/.next.RunPayload \
    -a com.wqry085.deployesystem.ADB_RUN_PAYLOAD \
    --es payload "your_payload_content"
```

### Android 12+ 配置

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| zyg1 | 换行填充数量 | 5-10 |
| zyg2 | 字符填充数量 | 0-8 |
| zyg3 | 尾部逗号数量 | 4-6 |

---

## 文件传输

### ZFTP 协议

```
协议头 (32 bytes):
┌────────┬───────┬───────┬────────┬────────┬─────────┬──────────┐
│Magic(4)│Ver(2) │Flags(2)│Files(4)│Dirs(4) │TotalSize│Checksum  │
│ "ZFTP" │0x0002 │   -   │   N    │   M    │  (8B)   │ CRC32    │
└────────┴───────┴───────┴────────┴────────┴─────────┴──────────┘

条目格式:
┌────────┬──────────┬──────┬──────────┬────────┬──────┐
│Type(1) │PathLen(2)│ Path │DataLen(8)│CRC32(4)│ Data │
└────────┴──────────┴──────┴──────────┴────────┴──────┘

结束标志: 0xFF
```

### 使用方法

1. 在应用中启动文件接收服务
2. 配置注入时指定 `--app-dir=/data/data/com.target.app:56423`
3. 执行注入后自动传输

---

## 故障排除

### 问题：注入无响应

```bash
# 检查进程
adb shell ps -A | grep zYg0te
```

### 问题：权限被拒绝

1. 确认 Shizuku 正常运行：`adb shell dumpsys activity service moe.shizuku.privileged.api`
2. MIUI 用户：开启「USB 调试（安全设置）」
3. 检查 SELinux：`adb shell getenforce`

### 问题：连接被拒绝

```bash
# 检查白名单状态
echo "控制密钥" | nc 127.0.0.1 8081
# 发送: POLICY_STATUS

# 添加当前 UID 到白名单
# 发送: POLICY_ADD <your_uid>
```

### 问题：文件传输失败

```bash
# 确认接收端口已监听
adb shell netstat -tlnp | grep 56423

# 检查传输日志
adb logcat -s FolderReceiver:* SocketSender:*
```

---

## 性能参考

| 指标 | 数值 |
|------|------|
| 文件传输速度 | 50-100 MB/s |
| Shell 响应延迟 | < 5ms |
| 内存占用 | ~10 MB |
| 线程池大小 | 4 |

---

## 安全说明

⚠️ **重要提示**：

1. **本地连接限制**：远程终端服务仅接受 127.0.0.1 连接
2. **白名单认证**：可配置 UID 白名单限制访问
3. **控制密钥**：控制接口需要 MD5 密钥认证(wqry085)
4. **自动清理**：Payload 注入后 200ms 自动重置系统设置
5. **路径安全**：文件传输防止 `..` 路径遍历攻击

---

## 开发工具

本项目开发过程中使用了以下 AI 辅助工具：

| 工具 | 用途 |
|------|------|
| Gemini 2.5/3 Pro | 代码优化 |
| Claude Opus 4.5 | 代码重构 |
| Claude 3.5 Sonnet | 代码审查 |
| DeepSeek V3.1 | 代码辅助 |

---

## 免责声明

本项目仅用于安全研究和教育目的，请勿用于非法用途。使用者需遵守当地法律法规，对使用本项目造成的任何后果负责。

**警告**：
- 本工具可能影响系统稳定性
- 不当使用可能导致设备异常
- 请在测试设备上使用

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 联系方式

- **GitHub**: [wqry085](https://github.com/wqry085)
- **Issues**: [提交问题](https://github.com/wqry085/PoC-Deployer-System/issues)

---

*请在使用前仔细阅读许可证中的免责条款和特别警告。*