# PoC-Deployer-System README.md

# PoC-Deployer-System

<div align="center">

![Android](https://img.shields.io/badge/Android-11~14-green.svg)
![API](https://img.shields.io/badge/API-30+-blue.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)
![Shizuku](https://img.shields.io/badge/Shizuku-Required-purple.svg)

**一个用于 Android Zygote 进程注入的概念验证（PoC）系统**

[English](#english) | [中文](#中文)

</div>

---

## 中文

### 📖 项目简介

PoC-Deployer-System 是一个 Android 安全研究工具，通过利用 `hidden_api_blacklist_exemptions` 系统设置实现 Zygote 进程注入。该工具提供远程 Shell 访问、文件传输、策略管理等功能，主要用于安全研究和系统调试目的。

> ⚠️ **警告**: 本工具仅供安全研究和教育目的使用。请勿用于非法用途。使用本工具可能会影响系统稳定性，请自行承担风险。

### ✨ 功能特性

| 功能 | 描述 |
|------|------|
| 🔧 **Zygote 注入** | 通过系统设置漏洞实现进程注入 |
| 💻 **远程 Shell** | 提供完整的 PTY 终端，支持窗口大小调整 |
| 📁 **文件传输** | 高性能文件夹传输协议，支持 CRC32 校验 |
| 🔐 **策略管理** | 基于 UID 的白名单访问控制 |
| 🎛️ **控制接口** | 支持远程命令控制和状态监控 |
| 📱 **ADB 集成** | 支持通过 ADB 直接调用 |

### 📋 系统要求

- **Android 版本**: 11 (API 30) - 14 (API 34)
- **权限要求**: Shizuku 或 Root
- **架构支持**: arm64-v8a, armeabi-v7a

### ⚠️ 特殊设备说明

| 设备/系统 | 问题 | 解决方案 |
|-----------|------|----------|
| MIUI/澎湃 | Shell 缺少 `WRITE_SECURE_SETTINGS` 权限 | 开启"USB 调试（安全设置）" |
| OPPO/ColorOS | 权限监控拦截 | 关闭"权限监控" |
| Vivo/OriginOS | 类似权限问题 | 进入开发者选项关闭相关限制 |

### 📦 安装

#### 方式一：从源码构建

```bash
# 克隆仓库
git clone https://github.com/wqry085/PoC-Deployer-System.git
cd PoC-Deployer-System

# 构建
./gradlew assembleDebug

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### 方式二：下载 Release

从 [Releases](https://github.com/wqry085/PoC-Deployer-System/releases) 下载最新版本 APK。

### 🚀 快速开始

#### 1. 授予 Shizuku 权限

确保已安装并激活 [Shizuku](https://shizuku.rikka.app/)，然后在应用中授予权限。

#### 2. 配置注入参数

在主界面配置以下参数：

| 参数 | 说明 | 示例 |
|------|------|------|
| UID | 目标进程用户 ID | `1000` (system) |
| GID | 目标进程组 ID | `1000` |
| SELinux | SELinux 上下文 | `platform:privapp:targetSdkVersion=29` |
| Nice Name | 进程名称 | `zYg0te` |
| Runtime Flags | 运行时标志 | `43267` |

#### 3. 执行注入

点击"执行"按钮，系统会：
1. 构建 Payload
2. 写入系统设置
3. 触发 Zygote 重新加载
4. 启动注入进程

#### 4. 连接 Shell

```bash
# 方式一：使用 nc
stty raw -echo; nc 127.0.0.1 8080; stty sane

# 方式二：使用 telnet
telnet 127.0.0.1 8080

# 方式三：使用应用内终端
点击"终端"按钮
```

### 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Android App                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │ ZygoteFragment  │  │   RunPayload    │  │ FolderReceiver │  │
│  │ (配置 & 控制)   │  │  (Payload加载)  │  │   (文件接收)   │  │
│  └────────┬────────┘  └────────┬────────┘  └───────┬────────┘  │
│           │                    │                    │           │
│           └────────────────────┴────────────────────┘           │
│                                │                                │
│                    ┌───────────▼───────────┐                   │
│                    │      Shizuku API      │                   │
│                    └───────────┬───────────┘                   │
└────────────────────────────────┼────────────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   hidden_api_blacklist  │
                    │      _exemptions        │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │     Zygote Process      │
                    │  ┌──────────────────┐   │
                    │  │  zygote_term.so  │   │
                    │  └────────┬─────────┘   │
                    └───────────┼─────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
    ┌─────────▼───────┐ ┌───────▼───────┐ ┌──────▼──────┐
    │  Shell Server   │ │Control Server │ │  Policy     │
    │   (Port 8080)   │ │  (Port 8081)  │ │  Daemon     │
    └─────────────────┘ └───────────────┘ └─────────────┘
```

### 📡 通信协议

#### Shell 协议 (Port 8080)

- 基于 PTY 的完整终端模拟
- 支持 Telnet NAWS 窗口大小协商
- 本地连接 + 策略白名单认证

#### 控制协议 (Port 8081)

```
认证: MD5("wqry085") -> 32位十六进制字符串

命令格式: COMMAND [ARGS]\n
响应格式: RESPONSE\n + COMMAND_PROCESSED\n
```

| 命令 | 说明 |
|------|------|
| `EXEC <cmd>` | 执行 Shell 命令 |
| `STATUS` | 获取系统状态 |
| `TERMINATE` | 终止系统 |
| `POLICY_ADD <uid>` | 添加 UID 到白名单 |
| `POLICY_REMOVE <uid>` | 从白名单移除 UID |
| `POLICY_LIST` | 列出白名单 |
| `SEND_APP_DIR` | 发送应用目录 |

#### 文件传输协议 (ZFTP)

```
Protocol Header (32 bytes):
┌──────────┬─────────┬───────┬───────────┬──────────┬──────────┬──────────┬──────────┐
│ Magic(4) │ Ver(2)  │Flags(2)│ Files(4) │ Dirs(4)  │ Size(8)  │Checksum(1)│Reserved(7)│
│  "ZFTP"  │ 0x0002  │   -   │    N      │    M     │  bytes   │  CRC32   │    -     │
└──────────┴─────────┴───────┴───────────┴──────────┴──────────┴──────────┴──────────┘

Entry Format:
┌──────────┬───────────┬─────────┬───────────┬──────────┬──────────┐
│ Type(1)  │PathLen(2) │  Path   │DataLen(8) │ CRC32(4) │   Data   │
│ 0x01/02  │    N      │N bytes  │  bytes    │(optional)│  bytes   │
└──────────┴───────────┴─────────┴───────────┴──────────┴──────────┘

End Marker: 0xFF
```

### 📁 项目结构

```
PoC-Deployer-System/
├── app/
│   ├── src/main/
│   │   ├── java/com/wqry085/deployesystem/
│   │   │   ├── ZygoteFragment.java      # 主配置界面
│   │   │   ├── TerminalActivity.java    # 终端界面
│   │   │   ├── next/
│   │   │   │   └── RunPayload.java      # Payload 加载器
│   │   │   └── sockey/
│   │   │       ├── FolderReceiver.java  # 文件接收器
│   │   │       └── ZygoteControlClient.java
│   │   │
│   │   ├── jni/
│   │   │   ├── zygote_term.c            # 主服务程序
│   │   │   ├── socket_sender.c          # 文件发送器
│   │   │   ├── socket_sender.h          # 协议定义
│   │   │   ├── policy_daemon.c          # 策略守护进程
│   │   │   └── policy_client.c          # 策略客户端
│   │   │
│   │   └── res/
│   │       ├── xml/root_preference.xml  # 配置界面
│   │       └── values/strings.xml       # 字符串资源
│   │
│   └── build.gradle
│
├── gradle/
├── build.gradle
└── README.md
```

### 🔧 高级用法

#### ADB 直接调用

```bash
# 加载 Payload 文件
adb shell am start -n com.wqry085.deployesystem/.next.RunPayload \
    -a com.wqry085.deployesystem.ADB_RUN_PAYLOAD \
    --es payload "$(cat payload.txt)"

# 提取应用数据
adb shell am start -n com.wqry085.deployesystem/.MainActivity \
    --es extract_app "com.target.package"
```

#### 自定义 Payload 格式

```
[换行填充 x N]
[字符填充 x M]
参数计数
--setuid=<UID>
--setgid=<GID>
--setgroups=<GROUPS>
--runtime-args
--seinfo=<SELINUX_CONTEXT>
--runtime-flags=<FLAGS>
--nice-name=<PROCESS_NAME>
--invoke-with
<SHELL_COMMAND>
```

#### Android 12+ 特殊配置

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| zyg1 | 换行填充数量 | 5-10 |
| zyg2 | 字符填充数量 | 0-8 |
| zyg3 | 尾部逗号数量 | 4-6 |

### 🐛 故障排除

#### 问题：注入后无响应

```bash
# 检查进程是否启动
adb shell ps -A | grep zYg0te

# 检查日志
adb logcat -s ZygoteTerm:* SocketSender:*

# 检查端口
adb shell netstat -tlnp | grep 8080
```

#### 问题：权限被拒绝

1. 检查 Shizuku 是否正常运行
2. 检查 USB 调试安全设置（MIUI）
3. 检查 SELinux 状态：`adb shell getenforce`

#### 问题：文件传输失败

```bash
# 检查接收端口
adb shell netstat -tlnp | grep 56423

# 手动测试连接
adb shell nc -zv 127.0.0.1 56423
```

### 📊 性能指标

| 指标 | 数值 |
|------|------|
| 文件传输速度 | ~50-100 MB/s (本地) |
| Shell 延迟 | <5ms |
| 内存占用 | ~10MB |
| CPU 占用 | <1% (空闲) |

### 🔒 安全注意事项

1. **本地连接限制**: Shell 服务仅接受来自 127.0.0.1 的连接
2. **白名单认证**: 可配置 UID 白名单限制访问
3. **控制密钥**: 控制接口需要 MD5 密钥认证
4. **自动清理**: Payload 注入后自动重置系统设置

### 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 📄 许可证

本项目基于 MIT 许可证开源 - 查看 [LICENSE](LICENSE) 文件了解详情。

### 🙏 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) - 提供 ADB 权限管理
- [AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) - 移动端开发环境

---

## English

### 📖 Introduction

PoC-Deployer-System is an Android security research tool that achieves Zygote process injection by exploiting the `hidden_api_blacklist_exemptions` system setting. It provides remote shell access, file transfer, policy management, and other features for security research and system debugging purposes.

> ⚠️ **Warning**: This tool is for security research and educational purposes only. Do not use for illegal purposes. Using this tool may affect system stability. Use at your own risk.

### ✨ Features

- 🔧 **Zygote Injection** - Process injection via system settings vulnerability
- 💻 **Remote Shell** - Full PTY terminal with window resize support
- 📁 **File Transfer** - High-performance folder transfer with CRC32 verification
- 🔐 **Policy Management** - UID-based whitelist access control
- 🎛️ **Control Interface** - Remote command control and status monitoring
- 📱 **ADB Integration** - Direct invocation via ADB

### 📋 Requirements

- **Android**: 11 (API 30) - 14 (API 34)
- **Permissions**: Shizuku or Root
- **Architecture**: arm64-v8a, armeabi-v7a

### 🚀 Quick Start

1. Install and activate [Shizuku](https://shizuku.rikka.app/)
2. Grant Shizuku permission to the app
3. Configure injection parameters (UID, GID, SELinux context, etc.)
4. Execute injection
5. Connect to shell: `nc 127.0.0.1 8080`

### 📄 License

MIT License - See [LICENSE](LICENSE) for details.

---

<div align="center">

**Made with ❤️ by wqry085**

</div>
```