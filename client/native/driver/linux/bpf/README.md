# SentinelHub LSM BPF (Phase 3/5)

Optional kernel-side process execution blocking via `bprm_check_security` LSM hook.

## Requirements

- Linux kernel >= 5.7 with `CONFIG_BPF_LSM=y`
- `clang`, `llvm`, `bpftool`, `libbpf` headers
- Root / `CAP_BPF` + `CAP_SYS_ADMIN`

## Build

```bash
cd client/native/driver/linux/bpf
make
```

## Load

```bash
sudo ./load.sh
```

Map pinned at `/sys/fs/bpf/sentinel_blocked_comms`.

## Auto-sync (Phase 5)

When BPF is loaded, `sentinel-driver` `set_policy` automatically updates the map from:

- DLP `process_block` rules with `action: block`
- Software policy blacklist with `action: block`

Manual update example:

```bash
echo -n "nc" | sudo bpftool map update pinned /sys/fs/bpf/sentinel_blocked_comms key -
```

## Fallback

When BPF is unavailable, `sentinel-driver` runs a userspace `/proc` watcher (`process_block.rs`).
