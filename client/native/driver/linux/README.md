# Linux kernel module (phase 1)

Loads a misc character device **`/dev/sentinelhub`** for policy push and status queries from the userspace `sentinel-driver` daemon.

## Build

Requires kernel headers for the running kernel:

```bash
sudo apt install linux-headers-$(uname -r)   # Debian/Ubuntu
cd client/native/driver/linux/sentinel-kmod
make
```

## Load / unload

```bash
sudo make load    # insmod sentinel_kmod.ko
ls -l /dev/sentinelhub
sudo make unload
```

## Ioctl API

See `client/native/driver/include/sentinel_ioctl.h`:

| Ioctl | Purpose |
|-------|---------|
| `SENTINEL_IOC_STATUS` | Kernel version, flags, policy length |
| `SENTINEL_IOC_SET_POLICY` | Push JSON policy blob (max 4 KiB) |
| `SENTINEL_IOC_GET_POLICY` | Read back stored policy |

## Integration

1. Load `sentinel_kmod.ko`
2. Start `sentinel-driver` — reports `mode: kernel_lsm` when `/dev/sentinelhub` is present
3. `sentinel-native enforce *` pushes policy to kernel when driver-assisted

## Roadmap (phase 2+)

- fanotify / LSM BPF hooks for file open interception
- process exec blocking via LSM
- event ring buffer to userspace
