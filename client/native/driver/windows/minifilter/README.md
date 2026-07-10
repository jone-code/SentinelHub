# Windows minifilter driver (phase 3)

Kernel-mode file system minifilter for DLP path blocking. Phase 3 implements policy cache and PreCreate path deny.

## Prerequisites

- Visual Studio 2022
- Windows Driver Kit (WDK) 10
- EV code signing certificate (production)

## Project layout

| File | Role |
|------|------|
| `SentinelFilter.c` | DriverEntry, comm port, policy cache, PreCreate deny |
| `SentinelFilter.h` | Policy structs, message types, callback prototypes |

## Communication port

Port name: `\\SentinelHubPort`

Message format (`SENTINEL_PORT_MESSAGE`):

| Field | Type | Description |
|-------|------|-------------|
| Type | ULONG | `SENTINEL_MSG_SET_POLICY` (1) or `SENTINEL_MSG_GET_STATUS` (2) |
| Length | ULONG | Payload bytes |
| Data | UCHAR[] | Policy JSON (same format as Linux daemon) |

On `SET_POLICY`, the driver parses `sensitive_path` / `file_hook` rules with `action: block` and caches patterns. `PreCreate` denies matching paths with `STATUS_ACCESS_DENIED`.

## Build (developer machine)

1. Create empty **Windows Driver → Filter Driver** project in Visual Studio
2. Add `SentinelFilter.c` / `SentinelFilter.h`
3. Build x64 Release
4. Install with `fltmc load SentinelHub` (service name TBD in INF)

## Roadmap

| Phase | Capability |
|-------|------------|
| 1 | Filter load/unload, pre-create pass-through |
| 2 | Communication port skeleton |
| 3 | Policy cache + path deny (current) |
| 4 | USB write blocking, process create callback |

## Testing

Use VM with test signing enabled:

```cmd
bcdedit /set testsigning on
```

Load driver and verify with `fltmc filters`.
