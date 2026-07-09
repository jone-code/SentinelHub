# SentinelHub Native Driver

Userspace driver daemon for deep enforcement IPC. Kernel minifilter hooks will attach in a future release.

## Components

| Binary | Role |
|--------|------|
| `sentinel-driver` | Unix socket daemon (`/tmp/sentinel-driver.sock`) |
| `sentinel-native driver status` | Probe daemon from sidecar |

## Build

```bash
cd client/native
cargo build --release
# sentinel-native: target/release/sentinel-native
# sentinel-driver: driver/sentinel-driver/target/release/sentinel-driver
```

## Run daemon

```bash
SENTINEL_DRIVER_SOCKET=/tmp/sentinel-driver.sock ./driver/sentinel-driver/target/release/sentinel-driver
```

Node service (`client/service`) auto-starts the daemon on boot when the binary is found.

## IPC protocol

JSON-line over Unix socket:

```json
{"cmd":"status"}
{"cmd":"ping"}
{"cmd":"capabilities"}
```

## Capabilities (userspace_daemon)

- `process_block` — software blacklist termination
- `usb_unmount` — DLP USB control
- `sensitive_path_scan` — DLP file scan
- `health_probe` — daemon liveness
