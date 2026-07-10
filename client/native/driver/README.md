# SentinelHub Native Driver

Kernel and userspace driver stack for deep enforcement.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Node service (client/service)                          │
│    driver-bridge.js → sentinel-native driver status     │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  sentinel-native (Rust)                                   │
│    enforce/* → driver::push_policy() when kernel loaded   │
└──────────────────────────┬──────────────────────────────┘
                           │ ioctl
┌──────────────────────────▼──────────────────────────────┐
│  Linux: /dev/sentinelhub (sentinel_kmod.ko)               │
│  Windows: SentinelFilter.sys (minifilter, WDK build)      │
└─────────────────────────────────────────────────────────┘
         ▲
         │ Unix socket (fallback)
┌────────┴────────┐
│ sentinel-driver │  userspace daemon
└─────────────────┘
```

## Components

| Path | Platform | Role |
|------|----------|------|
| `linux/sentinel-kmod/` | Linux | Loadable kernel module, ioctl policy channel |
| `windows/minifilter/` | Windows | Minifilter skeleton (phase 1) |
| `include/sentinel_ioctl.h` | Both | Shared ioctl definitions |
| `sentinel-driver/` | Linux/macOS | Userspace daemon + kernel bridge |

## Driver modes

| mode | Condition |
|------|-----------|
| `kernel_lsm` | `/dev/sentinelhub` present (Linux kmod loaded) |
| `userspace_daemon` | `sentinel-driver` socket reachable |
| `stub` | Neither available — userspace enforcers only |

## Quick start (Linux)

```bash
# 1. Build kernel module (on target machine with kernel headers)
cd client/native/driver/linux/sentinel-kmod
make && sudo make load

# 2. Build userspace binaries
cd client/native
cargo build --release

# 3. Start daemon (optional when kmod loaded; still useful for IPC)
./driver/sentinel-driver/target/release/sentinel-driver &

# 4. Verify
./target/release/sentinel-native driver status --json
```

## IPC protocol (daemon)

JSON-line over Unix socket:

```json
{"cmd":"status"}
{"cmd":"set_policy","policy":"{...}"}
```

## Phase roadmap

| Phase | Linux | Windows |
|-------|-------|---------|
| **1 (current)** | Char device + policy ioctl | Minifilter register + pre-create stub |
| 2 | fanotify / LSM BPF file hooks | Filter communication port + policy cache |
| 3 | Process exec blocking | Create-process callback |
| 4 | Event ring buffer to userspace | USB write blocking |
