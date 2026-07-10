# Windows minifilter driver (phase 1 skeleton)

Kernel-mode file system minifilter for DLP path blocking and audit. Phase 1 registers `IRP_MJ_CREATE` pre-operation callback stub.

## Prerequisites

- Visual Studio 2022
- Windows Driver Kit (WDK) 10
- EV code signing certificate (production)

## Project layout

| File | Role |
|------|------|
| `SentinelFilter.c` | `DriverEntry`, filter registration, pre-create stub |
| `SentinelFilter.h` | Callback prototypes |

## Build (developer machine)

1. Create empty **Windows Driver → Filter Driver** project in Visual Studio
2. Add `SentinelFilter.c` / `SentinelFilter.h`
3. Build x64 Release
4. Install with `fltmc load SentinelHub` (service name TBD in INF)

## Userspace communication

Policy channel mirrors Linux ioctl layout in `driver/include/sentinel_ioctl.h`. Phase 2 adds filter communication port (`FltCreateCommunicationPort`) for policy push from `sentinel-driver`.

## Roadmap

| Phase | Capability |
|-------|------------|
| 1 | Filter load/unload, pre-create pass-through (this skeleton) |
| 2 | Communication port + policy cache |
| 3 | Block sensitive path / USB writes |
| 4 | Process create callback driver |

## Testing

Use VM with test signing enabled:

```cmd
bcdedit /set testsigning on
```

Load driver and verify with `fltmc filters`.
