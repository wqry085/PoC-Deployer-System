# Binder CLI

<div align="center">

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)

**Command-line tool for Android Binder services**

</div>

## Overview

Binder CLI lets you interact with Android system services directly—list services, call methods, send raw transactions.

## Quick Start

```bash
# Alias setup
alias binder="$(pm path com.wqry085.deployesystem | cut -d: -f2 | xargs dirname | sed 's/$/\/lib\/arm64/')/libbinder.so"

# List all services
binder list

# Get service info
binder info activity

# List methods
binder methods package

# Call a method
binder call package getInstalledPackages 0
```

## Commands

| Command | Description |
|---------|-------------|
| `list, ls` | List Binder services |
| `methods, m` | List service methods |
| `call, c` | Call service method |
| `info, i` | Get service info |
| `ping, p` | Check if service alive |
| `transact` | Send raw transaction |
| `search` | Search services/methods |
| `interface` | Inspect interface |
| `export` | Export interface (Java/Kotlin/JSON) |
| `monitor` | Monitor service changes |
| `shell` | Interactive shell |
| `stats` | Show Binder statistics |
| `help` | Show help |

## Common Examples

```bash
# List services matching "activity"
binder list -f activity

# Call with JSON output
binder call package getPackageInfo com.android.settings 0 -j

# Show method signatures
binder methods package -s

# Force stop app
binder call activity forceStopPackage com.example.app 0

# Monitor service changes
binder monitor

# Export as Kotlin interface
binder export activity -f kotlin
```

## Argument Formats

| Type | Example |
|------|---------|
| Integer | `123`, `0xFF` |
| Long | `123L` |
| Boolean | `true`, `false` |
| String | `hello`, `"hello world"` |
| Array | `[1,2,3]` |

## Built-in Constants

```
FLAG_ACTIVITY_NEW_TASK     = 0x10000000
USER_CURRENT               = -2
USER_ALL                   = -1
IMPORTANCE_FOREGROUND      = 100
```

## Supported Services

Common pre-configured services: `activity`, `package`, `window`, `power`, `wifi`, `audio`, `notification`, `telephony`, `location`, `device_policy`

Others auto-discovered via interface descriptors.

## Notes

- Many methods require system permissions
- Some operations need root
- API availability varies by Android version

## Disclaimer

For research purposes only. Use responsibly on devices you own.