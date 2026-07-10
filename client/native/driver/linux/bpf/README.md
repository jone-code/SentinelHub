# SentinelHub LSM BPF (Phase 3)

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

## Populate blocked process names

```bash
# Example: block "nc" (netcat)
echo -n "nc" | sudo bpftool map update pinned /sys/fs/bpf/blocked_comms key -
```

## Fallback

When BPF is unavailable, `sentinel-driver` runs a userspace `/proc` watcher (`process_block.rs`) that terminates matching processes and pushes events to the kernel ring.
