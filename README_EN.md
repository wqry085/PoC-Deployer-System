# English Version README.md

# PoC-Deployer-System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)
[![Shizuku](https://img.shields.io/badge/Shizuku-Required-purple.svg)](https://shizuku.rikka.app/)

An Android Zygote injection tool based on CVE-2024-31317, featuring remote shell, file transfer, and policy management capabilities.

[Binder-CLI](./binder.md) If you are tired of the command line with system permissions, you can try deploying it to use binders to access system services

## Project Statement

### Acknowledgements
Although PoC-Deployer-System has become an independent project, special thanks to the original project for its inspiration and contributions:

**Original Project**: https://github.com/Webldix/CVE-2024-31317-PoC-Deployer

---

## Changelog

### v1.5.7 New Features
- **[Remote Terminal]** Full PTY terminal with window resize support
- **[App Data Extraction]** ZFTP protocol with CRC32 checksum, transfer speed 50-100 MB/s
- **[Policy Authorization]** UID whitelist-based access control ensures only authorized UIDs can access the remote terminal
- **[Control Interface]** Remote command execution and status monitoring
- **[ADB Integration]** Direct payload invocation via ADB

### v1.5.6 Features
- **[Custom uid/gid/SELinux context/groups injection]**
- **[App data extraction functionality]**
- **[Zygote log monitoring]**
- **[Additional Zygote parameters support]**

---

## Screenshots

### uid/gid/groups Injection
![App Screenshot](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a1.jpg)

### Advanced Features Interface
![App Screenshot 2](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a2.jpg)

### Reverse Shell
![App Screenshot 3](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a3.jpg)

---

## System Requirements

### Supported Environment

| Condition | Requirement |
|-----------|-------------|
| Security Patch | Before June 2024 |
| Android Version | 9 - 13 |
| Permission | Shizuku Permission |
| Architecture | arm64-v8a / armeabi-v7a / x86 / x86_64 |

### Device-Specific Notes

| Device/System | Issue | Solution |
|---------------|-------|----------|
| MIUI/HyperOS | Shell lacks `WRITE_SECURE_SETTINGS` permission | Enable "USB Debugging (Security Settings)" |
| OPPO/ColorOS | Permission monitoring blocks access | Disable "Permission Monitoring" |
| Vivo/OriginOS | Similar permission issues | Disable related restrictions in Developer Options |

---

## Key Features

### Core Functionality

| Feature | Description |
|---------|-------------|
| 🔧 **Zygote Injection** | Process injection via hidden_api_blacklist_exemptions |
| 💻 **Remote Terminal** | Full PTY terminal with Telnet NAWS protocol support |
| 📁 **App Data Extraction** | ZFTP protocol, 64KB buffer, CRC32 checksum |
| 🔐 **Policy Management** | UID whitelist with persistent storage |
| 🎛️ **Control Interface** | Remote command execution and status monitoring |
| 📱 **ADB Integration** | Silent mode direct invocation |

### Technical Highlights
- Privilege escalation with only Shizuku permission
- Multi-process architecture with automatic crash recovery
- Thread pool for concurrent connection handling
- High-performance I/O with epoll

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Android App                            │
│  ┌───────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ ZygoteFragment│  │  RunPayload │  │  FolderReceiver  │  │
│  │   (Config UI) │  │(Payload Load)│  │ (File Receiver)  │  │
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
│   Port 8080     │ │  Port 8081    │ │  (Whitelist)  │
└─────────────────┘ └───────────────┘ └───────────────┘
```

---

## Usage Guide

### Quick Start

1. **Install Shizuku** and activate it
2. **Install the app** and grant Shizuku permission
3. **Configure parameters**:

| Parameter | Description | Example |
|-----------|-------------|---------|
| UID | Target user ID | `1000` (system) |
| GID | Target group ID | `1000` |
| SELinux | SELinux context | `platform:privapp:targetSdkVersion=29` |
| Nice Name | Process name | `zYg0te` |
| Runtime Flags | Runtime flags | `43267` |

4. **Execute injection**
5. **Connect to reverse shell**:

```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

### Port Reference

| Port | Purpose | Authentication |
|------|---------|----------------|
| 8080 | Shell Service | Local connection + UID whitelist |
| 8081 | Control Interface | MD5 key authentication |
| 56423 | File Receiver | None (local only) |

### Control Commands

Available commands after connecting to the control interface:

```
EXEC <cmd>          - Execute shell command
STATUS              - Get system status
GET_HISTORY         - Get command history
TERMINATE           - Terminate system
POLICY_ADD <uid>    - Add UID to whitelist
POLICY_REMOVE <uid> - Remove UID from whitelist
POLICY_LIST         - List whitelist
SEND_APP_DIR        - Send app directory
HELP                - Show help
EXIT                - Disconnect
```

### ADB Direct Invocation

```bash
# Load Payload
adb shell am start -n com.wqry085.deployesystem/.next.RunPayload \
    -a com.wqry085.deployesystem.ADB_RUN_PAYLOAD \
    --es payload "your_payload_content"
```

### Android 12+ Configuration

| Parameter | Description | Recommended Value |
|-----------|-------------|-------------------|
| zyg1 | Newline padding count | 5-10 |
| zyg2 | Character padding count | 0-8 |
| zyg3 | Trailing comma count | 4-6 |

---

## File Transfer

### ZFTP Protocol

```
Protocol Header (32 bytes):
┌────────┬───────┬───────┬────────┬────────┬─────────┬──────────┐
│Magic(4)│Ver(2) │Flags(2)│Files(4)│Dirs(4) │TotalSize│Checksum  │
│ "ZFTP" │0x0002 │   -   │   N    │   M    │  (8B)   │ CRC32    │
└────────┴───────┴───────┴────────┴────────┴─────────┴──────────┘

Entry Format:
┌────────┬──────────┬──────┬──────────┬────────┬──────┐
│Type(1) │PathLen(2)│ Path │DataLen(8)│CRC32(4)│ Data │
└────────┴──────────┴──────┴──────────┴────────┴──────┘

End Marker: 0xFF
```

### How to Use

1. Start the file receiver service in the app
2. Specify `--app-dir=/data/data/com.target.app:56423` when configuring injection
3. Files transfer automatically after injection

---

## Troubleshooting

### Issue: No Response After Injection

```bash
# Check process
adb shell ps -A | grep zYg0te
```

### Issue: Permission Denied

1. Verify Shizuku is running: `adb shell dumpsys activity service moe.shizuku.privileged.api`
2. MIUI users: Enable "USB Debugging (Security Settings)"
3. Check SELinux: `adb shell getenforce`

### Issue: Connection Refused

```bash
# Check whitelist status
echo "control_key" | nc 127.0.0.1 8081
# Send: POLICY_STATUS

# Add current UID to whitelist
# Send: POLICY_ADD <your_uid>
```

### Issue: File Transfer Failed

```bash
# Verify receiver port is listening
adb shell netstat -tlnp | grep 56423

# Check transfer logs
adb logcat -s FolderReceiver:* SocketSender:*
```

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| File Transfer Speed | 50-100 MB/s |
| Shell Response Latency | < 5ms |
| Memory Usage | ~10 MB |
| Thread Pool Size | 4 |

---

## Security Notes

⚠️ **Important**:

1. **Local Connection Only**: Remote terminal service only accepts 127.0.0.1 connections
2. **Whitelist Authentication**: Configurable UID whitelist for access control
3. **Control Key**: Control interface requires MD5 key authentication (wqry085)
4. **Auto Cleanup**: System settings automatically reset 200ms after payload injection
5. **Path Security**: File transfer prevents `..` path traversal attacks

---

## Development Tools

The following AI-assisted tools were used during development:

| Tool | Purpose |
|------|---------|
| Gemini 2.5/3 Pro | Code optimization |
| Claude Opus 4.5 | Code refactoring |
| Claude 3.5 Sonnet | Code review |
| DeepSeek V3.1 | Code assistance |

---

## Disclaimer

This project is intended for security research and educational purposes only. Do not use for illegal activities. Users must comply with local laws and regulations and are responsible for any consequences resulting from the use of this project.

**Warning**:
- This tool may affect system stability
- Improper use may cause device malfunction
- Please use on test devices only

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

---

## Contact

- **GitHub**: [wqry085](https://github.com/wqry085)
- **Issues**: [Submit an Issue](https://github.com/wqry085/PoC-Deployer-System/issues)

---

*Please read the disclaimer and warnings in the license carefully before use.*