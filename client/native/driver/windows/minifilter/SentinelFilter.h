/* SentinelHub Windows minifilter driver — phase 3.
 * Build with Windows Driver Kit (WDK) + Visual Studio.
 * See README.md in this directory.
 */

#pragma once

#include <fltKernel.h>

#define SENTINEL_FILTER_NAME L"SentinelHub Filter"
#define SENTINEL_FILTER_TAG  'SntH'
#define SENTINEL_PORT_NAME   L"\\SentinelHubPort"

#define SENTINEL_POLICY_MAX    4096
#define SENTINEL_MAX_RULES     32
#define SENTINEL_PATTERN_MAX   128

#define SENTINEL_MSG_SET_POLICY  1
#define SENTINEL_MSG_GET_STATUS  2

typedef struct _SENTINEL_PORT_MESSAGE {
    ULONG Type;
    ULONG Length;
    UCHAR Data[1];
} SENTINEL_PORT_MESSAGE, *PSENTINEL_PORT_MESSAGE;

typedef struct _SENTINEL_POLICY_RULE {
    BOOLEAN Block;
    WCHAR Pattern[SENTINEL_PATTERN_MAX];
} SENTINEL_POLICY_RULE, *PSENTINEL_POLICY_RULE;

typedef struct _SENTINEL_POLICY_CACHE {
    ULONG RuleCount;
    BOOLEAN UsbBlockWrites;
    SENTINEL_POLICY_RULE Rules[SENTINEL_MAX_RULES];
} SENTINEL_POLICY_CACHE, *PSENTINEL_POLICY_CACHE;

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

FLT_PREOP_CALLBACK_STATUS SentinelPreWrite(
    _Inout_ PFLT_CALLBACK_DATA Data,
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _Flt_CompletionContext_Outptr_ PVOID *CompletionContext);

BOOLEAN SentinelVolumeIsRemovable(_In_ PCFLT_RELATED_OBJECTS FltObjects);

NTSTATUS SentinelUpdatePolicyCache(
    _In_reads_bytes_(Length) PUCHAR Data,
    _In_ ULONG Length);

BOOLEAN SentinelParsePolicyPatterns(
    _In_reads_bytes_(Length) PUCHAR Data,
    _In_ ULONG Length,
    _Out_ PSENTINEL_POLICY_CACHE Cache);

BOOLEAN SentinelPathMatchesRule(
    _In_ PCUNICODE_STRING Path,
    _In_ PCWSTR Pattern);

BOOLEAN SentinelStrContains(
    _In_ PCSTR Haystack,
    _In_ PCSTR Needle);
