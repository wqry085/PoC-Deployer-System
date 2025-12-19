# Binder CLI

<div align="center">

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)

**A powerful command-line tool for interacting with Android Binder services**

</div>

---

## 📖 Overview

Binder CLI is a comprehensive command-line utility for Android that allows developers and security researchers to interact with system Binder services directly. It provides capabilities to list services, invoke methods, monitor service states, send raw transactions, and much more.

## ✨ Features

- 🔍 **Service Discovery** - List and filter all registered Binder services
- 📞 **Method Invocation** - Call service methods with automatic type conversion
- 📊 **Service Inspection** - View detailed service information and method signatures
- 🔔 **Real-time Monitoring** - Monitor service registration/deregistration
- 📦 **Raw Transactions** - Send low-level Binder transactions
- 🔎 **Search Functionality** - Search across services and methods
- 📤 **Interface Export** - Export interface definitions (Java/Kotlin/JSON)
- 💻 **Interactive Shell** - Built-in REPL for interactive exploration
- 🎨 **Multiple Output Formats** - Support for JSON, raw, and formatted output

### Run

```bash
# Shell
id

uid=1000(system).....

alias binder="$(pm path com.wqry085.deployesystem | cut -d: -f2 | xargs dirname | sed 's/$/\/lib\/arm64/')/libbinder.so"

binder

Binder Command Line Tool v2.0.0
==========================================
Usage: binder <command> [options] [args...]

Commands:
  list, ls              List all Binder services
  methods, m <service>  List available methods
  call, c <service> <method> [args]
                        Call service method
  info, i <service>     Get service information
  ping, p <service>     Check if service is alive
  dump, d <service>     Dump service state
  monitor               Monitor service changes
  transact <service> <code> <data>
                        Send raw transaction
  search <pattern>      Search services/methods
  interface <name>      Inspect interface
  constants [class]     Show/extract constants
  export <service>      Export interface definition
  shell                 Start interactive shell
  stats                 Show Binder statistics
  help [command]        Show help
  version               Show version

Use 'binder help <command>' for more info.
```

## 🚀 Usage

### Basic Syntax

```bash
binder <command> [options] [arguments...]
```

### Quick Start

```bash
# List all services
binder list

# Get service info
binder info activity

# List methods of a service
binder methods package

# Call a service method
binder call package getInstalledPackages 0
```

## 📚 Commands

### Service Discovery

| Command | Alias | Description |
|---------|-------|-------------|
| `list` | `ls` | List all Binder services |
| `info` | `i` | Get detailed service information |
| `ping` | `p` | Check if service is alive |
| `search` | `find` | Search services and methods |

### Method Operations

| Command | Alias | Description |
|---------|-------|-------------|
| `methods` | `m` | List available methods |
| `call` | `c` | Call a service method |
| `transact` | - | Send raw transaction |

### Analysis & Export

| Command | Alias | Description |
|---------|-------|-------------|
| `interface` | `iface` | Inspect interface definition |
| `constants` | `const` | Show/extract constants |
| `export` | - | Export interface (Java/Kotlin/JSON) |
| `dump` | `d` | Dump service state |

### Monitoring & Tools

| Command | Description |
|---------|-------------|
| `monitor` | Monitor service changes in real-time |
| `shell` | Start interactive shell |
| `stats` | Show Binder statistics |
| `batch` | Execute commands from file |

---

## 📋 Command Reference

### `list` - List Services

```bash
binder list [options]

Options:
  -a, --all        Show all services (including dead)
  -d, --dead       Show only dead services
  -c, --compact    Compact output (names only)
  -f, --filter     Filter by name pattern (regex)
```

**Examples:**
```bash
# List all alive services
binder list

# List only services matching "activity"
binder list -f activity

# Compact list for scripting
binder list -c
```

---

### `call` - Call Service Method

```bash
binder call <service> <method> [options] [args...]

Options:
  -t, --types <types>    Specify parameter types (comma-separated)
  -u, --user <userId>    Specify user ID
  -j, --json             Output result as JSON
  -r, --raw              Output raw result
```

**Argument Formats:**

| Type | Format | Example |
|------|--------|---------|
| Integer | Decimal, hex, binary | `123`, `0xFF`, `0b1010` |
| Long | With L suffix | `123L`, `0xFFL` |
| Boolean | true/false or 1/0 | `true`, `0` |
| String | Plain or quoted | `hello`, `"hello world"` |
| Null | null keyword | `null` |
| Array | Bracketed | `[1,2,3]`, `[]` |
| ComponentName | package/class | `com.example/.MainActivity` |
| Uri | Standard format | `content://...` |

**Built-in Constants:**

```
FLAG_ACTIVITY_NEW_TASK     = 0x10000000
FLAG_ACTIVITY_CLEAR_TOP    = 0x04000000
FLAG_ACTIVITY_SINGLE_TOP   = 0x20000000
USER_CURRENT               = -2
USER_ALL                   = -1
USER_SYSTEM                = 0
IMPORTANCE_FOREGROUND      = 100
IMPORTANCE_BACKGROUND      = 400
```

**Examples:**
```bash
# Get installed packages with flags
binder call package getInstalledPackages 0

# Force stop an app
binder call activity forceStopPackage com.example.app 0

# Call with explicit types
binder call activity startActivity null null android.intent.action.VIEW null null null null 0 null null -t Intent,String,String,Uri,String,IBinder,String,int,ProfilerInfo,Bundle

# JSON output
binder call package getPackageInfo com.android.settings 0 -j
```

---

### `methods` - List Methods

```bash
binder methods <service> [options]

Options:
  -f, --filter <pattern>   Filter methods by name
  -p, --params             Show parameter types
  -r, --return             Show return types
  -s, --signature          Show full method signatures
```

**Examples:**
```bash
# List all methods
binder methods activity

# Show methods with signatures
binder methods package -s

# Filter methods containing "install"
binder methods package -f install -p -r
```

---

### `info` - Service Information

```bash
binder info <service> [options]

Options:
  -v, --verbose    Show verbose information
  -j, --json       Output as JSON
```

**Example Output:**
```
Service Information: activity
==========================================
  Name:      activity
  Interface: android.app.IActivityManager
  Status:    ✓ ALIVE
  Class:     android.app.IActivityManager$Stub$Proxy
  Methods:   156
  Stub:      android.app.IActivityManager$Stub
```

---

### `ping` - Ping Service

```bash
binder ping <service> [options]

Options:
  -c, --count <n>       Ping n times
  -i, --interval <ms>   Interval between pings (default: 1000)
```

**Example:**
```bash
binder ping activity -c 5 -i 500
```

---

### `transact` - Raw Transaction

```bash
binder transact <service> <code> <data>

Arguments:
  code    Transaction code (integer)
  data    Hex-encoded data or 'empty' for empty parcel
```

**Example:**
```bash
# Send empty transaction
binder transact activity 1 empty

# Send hex data
binder transact activity 1 0102030405
```

---

### `interface` - Inspect Interface

```bash
binder interface <interface-name>
```

**Example:**
```bash
binder interface android.app.IActivityManager
```

Shows transaction codes and method definitions.

---

### `export` - Export Interface

```bash
binder export <service> [options]

Options:
  -f, --format <format>   Output format: java, kotlin, json
  -o, --output <file>     Output file (not implemented)
```

**Examples:**
```bash
# Export as Java interface
binder export activity -f java

# Export as Kotlin interface
binder export activity -f kotlin

# Export as JSON schema
binder export activity -f json
```

---

### `monitor` - Monitor Services

```bash
binder monitor
```

Real-time monitoring of service registration and removal. Press Ctrl+C to stop.

**Output:**
```
Monitoring Binder services...
Press Ctrl+C to stop
==========================================
[+] New service: my_new_service
[-] Removed service: old_service
```

---

### `shell` - Interactive Shell

```bash
binder shell
```

Starts an interactive REPL:
```
Binder Interactive Shell v2.0.0
Type 'help' for commands, 'exit' to quit

binder> list -c
activity
package
window
...

binder> info activity
...

binder> exit
Goodbye!
```

---

## 💡 Examples

### Common Use Cases

#### 1. List Running Activities
```bash
binder call activity getTasks 10 0
```

#### 2. Get Package Info
```bash
binder call package getPackageInfo com.android.settings 0 -j
```

#### 3. Check App Installation
```bash
binder call package getInstalledPackages 0 | grep "com.example"
```

#### 4. Force Stop Application
```bash
binder call activity forceStopPackage com.example.app 0
```

#### 5. Get Device Policy Status
```bash
binder methods device_policy -s
binder call device_policy isDeviceOwner null
```

#### 6. WiFi Operations
```bash
binder call wifi getWifiEnabledState
binder call wifi setWifiEnabled true
```

#### 7. Export Interface for Development
```bash
binder export package -f kotlin > IPackageManager.kt
```

---

## 📊 Supported Services

Pre-configured service mappings:

| Service Name | Interface |
|--------------|-----------|
| `activity` | `android.app.IActivityManager` |
| `package` | `android.content.pm.IPackageManager` |
| `window` | `android.view.IWindowManager` |
| `power` | `android.os.IPowerManager` |
| `wifi` | `android.net.wifi.IWifiManager` |
| `audio` | `android.media.IAudioService` |
| `notification` | `android.app.INotificationManager` |
| `telephony` | `com.android.internal.telephony.ITelephony` |
| `location` | `android.location.ILocationManager` |
| `bluetooth` | `android.bluetooth.IBluetoothManager` |
| `clipboard` | `android.content.IClipboard` |
| `input` | `android.hardware.input.IInputManager` |
| `display` | `android.hardware.display.IDisplayManager` |
| `usb` | `android.hardware.usb.IUsbManager` |
| `user` | `android.os.IUserManager` |
| `appops` | `com.android.internal.app.IAppOpsService` |
| `device_policy` | `android.app.admin.IDevicePolicyManager` |

*Other services are auto-discovered via interface descriptors.*

---

## ⚠️ Notes & Limitations

### Permissions
- Many service methods require system permissions
- Some operations require root access
- Running as shell user provides limited access

### Compatibility
- Tested on Android 8.0 - 14
- API availability varies by Android version
- Some interfaces are device-specific

### Security Considerations
- This tool can access sensitive system services
- Use responsibly and only on devices you own/control
- Not intended for malicious purposes

---

## 🔧 Troubleshooting

### "Service not found"
```bash
# Check if service exists
binder list -f <service-name>

# Check service status
binder ping <service-name>
```

### "Method not found"
```bash
# List available methods
binder methods <service> -s

# Check for similar method names
binder methods <service> -f <partial-name>
```

### "Cannot find Stub class"
The tool may not recognize custom service interfaces. Check the service descriptor:
```bash
binder info <service> -v
```