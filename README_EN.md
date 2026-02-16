# PoC-Deployer-System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)

A visualization tool based on CVE-2024-31317 for Zygote injection, integrating remote terminal and file transfer capabilities.

[Binder-CLI](./binder.md) - Access system services via binder

## Screenshots

| UID/GID Injection | Advanced Features | Reverse Shell |
|--------------|----------|------------|
| ![injection](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a1.jpg) | ![advanced](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a2.jpg) | ![shell](https://raw.githubusercontent.com/wqry085/PoC-Deployer-System/main/jpg/a3.jpg) |

## Quick Start

### Requirements
- Android 9-13, security patch before 2024.6
- Shizuku activated
- Disable vendor restrictions on special devices (MIUI/ColorOS/OriginOS)

### Usage
1. Install and activate Shizuku
2. Install app, grant Shizuku permission
3. Configure target parameters (UID/GID/SELinux context)
4. Start remote terminal (reverse shell)
5. Connect to reverse shell:
```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

## Features

| Feature | Description |
|------|------|
| Zygote Injection | via hidden_api_blacklist_exemptions |
| Remote Terminal | Full PTY, window resize support |
| App Data Transfer | 50-100 MB/s transfer speed |
| Access Control | UID whitelist |

## Ports

| Port | Purpose | Authentication |
|------|------|------|
| 8080 | Local reverse shell | Local + UID whitelist |
| 8081 | Control interface | MD5 key |
| 56423 | File接收 | Local |

## Control Commands (Port 8081)
```
EXEC <cmd>       - Execute command
STATUS           - System status
POLICY_ADD <uid> - Add to whitelist
POLICY_LIST      - List whitelist
SEND_APP_DIR     - Send app directory
```

## Troubleshooting
```bash
# Check process
ps -A | grep zYg0te
# Check ports
netstat -tlnp | grep 56423
# View logs
logcat -s FolderReceiver:*
```

## Disclaimer
For security research only. Illegal use is prohibited. Users assume all responsibility.

## Credits
https://github.com/Webldix