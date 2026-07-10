/* SentinelHub Windows minifilter driver — phase 1 skeleton.
 * Build with Windows Driver Kit (WDK) + Visual Studio.
 * See README.md in this directory.
 */

#pragma once

#include <fltKernel.h>

#define SENTINEL_FILTER_NAME L"SentinelHub Filter"
#define SENTINEL_FILTER_TAG  'SntH'
#define SENTINEL_PORT_NAME   L"\\SentinelHubPort"

extern PFLT_FILTER g_sentinel_filter;
extern PFLT_PORT g_sentinel_server_port;
extern PFLT_PORT g_sentinel_client_port;

NTSTATUS SentinelInstanceSetup(
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _In_ FLT_INSTANCE_SETUP_FLAGS Flags,
    _In_ DEVICE_TYPE VolumeDeviceType,
    _In_ FLT_FILESYSTEM_TYPE VolumeFilesystemType);

VOID SentinelInstanceTeardownStart(
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _In_ FLT_INSTANCE_TEARDOWN_FLAGS Reason);

NTSTATUS SentinelUnload(_In_ FLT_FILTER_UNLOAD_FLAGS Flags);

FLT_PREOP_CALLBACK_STATUS SentinelPreCreate(
    _Inout_ PFLT_CALLBACK_DATA Data,
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _Flt_CompletionContext_Outptr_ PVOID *CompletionContext);
