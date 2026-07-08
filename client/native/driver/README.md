# SentinelHub Native Driver (P2+)

Kernel-level or privileged driver module for deep enforcement capabilities:

- File-system minifilter (DLP block before write to USB)
- Network isolation hooks
- Process protection

## Current status

**Stub only.** Userspace enforcers in `src/enforce/` handle P2 MVP:

- `enforce software` — process termination
- `enforce dlp` — USB umount + sensitive file scan
- `enforce nac` — compliance score evaluation

## Planned IPC

```
Node service → sentinel-native driver status --json
             → sentinel-native driver <command> (future)
             → driver daemon (Rust/C) over Unix socket
```

Check driver availability:

```bash
sentinel-native driver status --json
```

## Build

Driver module will be a separate crate under `driver/sentinel-driver/` when implemented.
