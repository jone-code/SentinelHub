# sentinel-native

Rust sidecar for deep system integration. Node orchestration layer (`client/service/`) spawns this binary; Flutter UI never calls it directly.

## Why a sidecar

| Layer | Role |
|-------|------|
| Flutter | UI only |
| Node.js | Cloud API, scheduling, local IPC |
| **sentinel-native** | OS APIs, collectors, enforcers, driver IPC (P2) |

## Build

```bash
cd client/native
cargo build --release
# binary: target/release/sentinel-native
```

Node auto-detects `../../native/target/release/sentinel-native`, or set `SENTINEL_NATIVE_BIN`.

## CLI

```bash
sentinel-native collect --json
sentinel-native enforce software --policy-file /path/to/policy.json --json
```

## Roadmap

| Phase | Commands |
|-------|----------|
| P0 | `collect` — hardware snapshot |
| P1 | `enforce software` — blacklist process detection |
| P2 | DLP / NAC via driver communication |
