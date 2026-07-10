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
| `linux/bpf/` | Linux | Optional LSM BPF process block (phase 3) |
| `windows/minifilter/` | Windows | Minifilter + policy cache + path deny (phase 3) |
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
{"cmd":"get_events","limit":20}
{"cmd":"drain_kernel_events"}
```

`get_events` returns `{ file_events, process_events }`.

### Native CLI

```bash
sentinel-native driver events --limit 50 --json
```

Node service polls this every `CLIENT_DRIVER_EVENT_INTERVAL_SEC` (default 3) and reports via `POST /api/client/v1/service/report/events`.

## Phase roadmap

| Phase | Linux | Windows |
|-------|-------|---------|
| **1** | Char device + policy ioctl | Minifilter register + pre-create stub |
| **2** | fanotify file hooks + kernel event ring | FltCreateCommunicationPort skeleton |
| **3** | process_block watcher + LSM BPF skeleton | Policy cache + PreCreate path deny |
| **4 (current)** | Event stream → Node service → backend audit | (userspace client TBD) |
| 5 | BPF map auto-sync | USB write blocking |
