/* SentinelHub Windows minifilter — phase 3: policy cache + path deny. */

#include "SentinelFilter.h"

PFLT_FILTER g_sentinel_filter = NULL;
PFLT_PORT g_sentinel_server_port = NULL;
PFLT_PORT g_sentinel_client_port = NULL;

static SENTINEL_POLICY_CACHE gPolicyCache;
static KSPIN_LOCK gPolicyLock;

static const FLT_OPERATION_REGISTRATION callbacks[] = {
    { IRP_MJ_CREATE, 0, SentinelPreCreate, NULL },
    { IRP_MJ_OPERATION_END }
};

static const FLT_REGISTRATION filter_registration = {
    sizeof(FLT_REGISTRATION),
    FLT_REGISTRATION_VERSION,
    0,
    NULL,
    callbacks,
    SentinelUnload,
    SentinelInstanceSetup,
    SentinelInstanceTeardownStart,
    NULL,
    NULL,
    NULL
};

static NTSTATUS SentinelConnectNotify(
    _In_ PFLT_PORT ClientPort,
    _In_opt_ PVOID ServerPortCookie,
    _In_reads_bytes_opt_(SizeOfContext) PVOID ConnectionContext,
    _In_ ULONG SizeOfContext,
    _Outptr_result_maybenull_ PVOID *ConnectionPortCookie)
{
    UNREFERENCED_PARAMETER(ServerPortCookie);
    UNREFERENCED_PARAMETER(ConnectionContext);
    UNREFERENCED_PARAMETER(SizeOfContext);
    UNREFERENCED_PARAMETER(ConnectionPortCookie);
    g_sentinel_client_port = ClientPort;
    return STATUS_SUCCESS;
}

static VOID SentinelDisconnectNotify(_In_opt_ PVOID ConnectionCookie)
{
    UNREFERENCED_PARAMETER(ConnectionCookie);
    if (g_sentinel_client_port) {
        FltCloseClientPort(g_sentinel_filter, &g_sentinel_client_port);
        g_sentinel_client_port = NULL;
    }
}

static NTSTATUS SentinelMessageNotify(
    _In_opt_ PVOID PortCookie,
    _In_reads_bytes_opt_(InputBufferLength) PVOID InputBuffer,
    _In_ ULONG InputBufferLength,
    _Out_writes_bytes_to_opt_(OutputBufferLength, *ReturnOutputBufferLength) PVOID OutputBuffer,
    _In_ ULONG OutputBufferLength,
    _Out_ PULONG ReturnOutputBufferLength)
{
    PSENTINEL_PORT_MESSAGE msg;
    NTSTATUS status = STATUS_SUCCESS;

    UNREFERENCED_PARAMETER(PortCookie);

    if (!InputBuffer || InputBufferLength < sizeof(SENTINEL_PORT_MESSAGE)) {
        return STATUS_INVALID_PARAMETER;
    }

    msg = (PSENTINEL_PORT_MESSAGE)InputBuffer;
    if (msg->Length > InputBufferLength - FIELD_OFFSET(SENTINEL_PORT_MESSAGE, Data)) {
        return STATUS_INVALID_PARAMETER;
    }

    switch (msg->Type) {
    case SENTINEL_MSG_SET_POLICY:
        status = SentinelUpdatePolicyCache(msg->Data, msg->Length);
        break;
    case SENTINEL_MSG_GET_STATUS:
        break;
    default:
        status = STATUS_NOT_SUPPORTED;
        break;
    }

    if (OutputBuffer && OutputBufferLength >= sizeof(ULONG)) {
        *(PULONG)OutputBuffer = NT_SUCCESS(status) ? gPolicyCache.RuleCount : 0;
        *ReturnOutputBufferLength = sizeof(ULONG);
    } else {
        *ReturnOutputBufferLength = 0;
    }
    return status;
}

static NTSTATUS SentinelCreateCommunicationPort(_In_ PFLT_FILTER Filter)
{
    OBJECT_ATTRIBUTES oa;
    UNICODE_STRING portName;
    PSECURITY_DESCRIPTOR sd = NULL;
    NTSTATUS status;

    status = FltBuildDefaultSecurityDescriptor(&sd, FLT_PORT_ALL_ACCESS);
    if (!NT_SUCCESS(status)) {
        return status;
    }

    RtlInitUnicodeString(&portName, SENTINEL_PORT_NAME);
    InitializeObjectAttributes(&oa, &portName, OBJ_CASE_INSENSITIVE | OBJ_KERNEL_HANDLE, NULL, sd);

    status = FltCreateCommunicationPort(
        Filter,
        &g_sentinel_server_port,
        &oa,
        NULL,
        SentinelConnectNotify,
        SentinelDisconnectNotify,
        SentinelMessageNotify,
        1);

    FltFreeSecurityDescriptor(sd);
    return status;
}

NTSTATUS DriverEntry(
    _In_ PDRIVER_OBJECT DriverObject,
    _In_ PUNICODE_STRING RegistryPath)
{
    NTSTATUS status;
    UNREFERENCED_PARAMETER(RegistryPath);

    KeInitializeSpinLock(&gPolicyLock);
    RtlZeroMemory(&gPolicyCache, sizeof(gPolicyCache));

    status = FltRegisterFilter(DriverObject, &filter_registration, &g_sentinel_filter);
    if (!NT_SUCCESS(status)) {
        return status;
    }

    status = SentinelCreateCommunicationPort(g_sentinel_filter);
    if (!NT_SUCCESS(status)) {
        FltUnregisterFilter(g_sentinel_filter);
        g_sentinel_filter = NULL;
        return status;
    }

    return FltStartFiltering(g_sentinel_filter);
}

NTSTATUS SentinelUnload(_In_ FLT_FILTER_UNLOAD_FLAGS Flags)
{
    UNREFERENCED_PARAMETER(Flags);
    if (g_sentinel_server_port) {
        FltCloseCommunicationPort(g_sentinel_server_port);
        g_sentinel_server_port = NULL;
    }
    if (g_sentinel_filter) {
        FltUnregisterFilter(g_sentinel_filter);
        g_sentinel_filter = NULL;
    }
    return STATUS_SUCCESS;
}

NTSTATUS SentinelInstanceSetup(
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _In_ FLT_INSTANCE_SETUP_FLAGS Flags,
    _In_ DEVICE_TYPE VolumeDeviceType,
    _In_ FLT_FILESYSTEM_TYPE VolumeFilesystemType)
{
    UNREFERENCED_PARAMETER(FltObjects);
    UNREFERENCED_PARAMETER(Flags);
    UNREFERENCED_PARAMETER(VolumeDeviceType);
    UNREFERENCED_PARAMETER(VolumeFilesystemType);
    return STATUS_SUCCESS;
}

VOID SentinelInstanceTeardownStart(
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _In_ FLT_INSTANCE_TEARDOWN_FLAGS Reason)
{
    UNREFERENCED_PARAMETER(FltObjects);
    UNREFERENCED_PARAMETER(Reason);
}

FLT_PREOP_CALLBACK_STATUS SentinelPreCreate(
    _Inout_ PFLT_CALLBACK_DATA Data,
    _In_ PCFLT_RELATED_OBJECTS FltObjects,
    _Flt_CompletionContext_Outptr_ PVOID *CompletionContext)
{
    PFLT_FILE_NAME_INFORMATION nameInfo = NULL;
    NTSTATUS status;
    ULONG i;
    KIRQL oldIrql;
    BOOLEAN deny = FALSE;

    UNREFERENCED_PARAMETER(FltObjects);
    UNREFERENCED_PARAMETER(CompletionContext);

    if (!FLT_IS_IRP_OPERATION(Data)) {
        return FLT_PREOP_SUCCESS_NO_CALLBACK;
    }

    status = FltGetFileNameInformation(
        Data,
        FLT_FILE_NAME_NORMALIZED | FLT_FILE_NAME_QUERY_DEFAULT,
        &nameInfo);
    if (!NT_SUCCESS(status)) {
        return FLT_PREOP_SUCCESS_NO_CALLBACK;
    }

    status = FltParseFileNameInformation(nameInfo);
    if (!NT_SUCCESS(status)) {
        FltReleaseFileNameInformation(nameInfo);
        return FLT_PREOP_SUCCESS_NO_CALLBACK;
    }

    KeAcquireSpinLock(&gPolicyLock, &oldIrql);
    for (i = 0; i < gPolicyCache.RuleCount; i++) {
        if (!gPolicyCache.Rules[i].Block) {
            continue;
        }
        if (SentinelPathMatchesRule(&nameInfo->Name, gPolicyCache.Rules[i].Pattern)) {
            deny = TRUE;
            break;
        }
    }
    KeReleaseSpinLock(&gPolicyLock, oldIrql);

    FltReleaseFileNameInformation(nameInfo);

    if (deny) {
        Data->IoStatus.Status = STATUS_ACCESS_DENIED;
        Data->IoStatus.Information = 0;
        return FLT_PREOP_COMPLETE;
    }

    return FLT_PREOP_SUCCESS_NO_CALLBACK;
}

NTSTATUS SentinelUpdatePolicyCache(_In_reads_bytes_(Length) PUCHAR Data, _In_ ULONG Length)
{
    SENTINEL_POLICY_CACHE parsed;
    KIRQL oldIrql;

    RtlZeroMemory(&parsed, sizeof(parsed));
    if (!SentinelParsePolicyPatterns(Data, Length, &parsed)) {
        return STATUS_INVALID_PARAMETER;
    }

    KeAcquireSpinLock(&gPolicyLock, &oldIrql);
    gPolicyCache = parsed;
    KeReleaseSpinLock(&gPolicyLock, oldIrql);
    return STATUS_SUCCESS;
}

BOOLEAN SentinelParsePolicyPatterns(
    _In_reads_bytes_(Length) PUCHAR Data,
    _In_ ULONG Length,
    _Out_ PSENTINEL_POLICY_CACHE Cache)
{
    CHAR *buf;
    ULONG i;
    PCHAR cursor;
    PCHAR channel;
    PCHAR action;
    PCHAR patterns;
    PCHAR pat;
    PCHAR end;

    if (!Data || Length == 0 || Length > SENTINEL_POLICY_MAX || !Cache) {
        return FALSE;
    }

    buf = (CHAR *)ExAllocatePool2(POOL_FLAG_NON_PAGED, Length + 1, SENTINEL_FILTER_TAG);
    if (!buf) {
        return FALSE;
    }

    RtlCopyMemory(buf, Data, Length);
    buf[Length] = '\0';

    cursor = buf;
    while (cursor < buf + Length && Cache->RuleCount < SENTINEL_MAX_RULES) {
        channel = strstr(cursor, "\"channel\"");
        if (!channel) {
            break;
        }
        channel = strstr(channel, ":");
        if (!channel) {
            break;
        }
        channel = strstr(channel, "\"");
        if (!channel) {
            break;
        }
        channel++;
        if (!SentinelStrContains(channel, "sensitive_path") &&
            !SentinelStrContains(channel, "file_hook")) {
            cursor = channel + 1;
            continue;
        }

        action = strstr(channel, "\"action\"");
        if (!action) {
            cursor = channel + 1;
            continue;
        }
        action = strstr(action, "\"");
        if (!action) {
            cursor = channel + 1;
            continue;
        }
        action++;
        if (!SentinelStrContains(action, "block")) {
            cursor = channel + 1;
            continue;
        }

        patterns = strstr(channel, "\"patterns\"");
        if (!patterns) {
            cursor = channel + 1;
            continue;
        }
        patterns = strchr(patterns, '[');
        if (!patterns) {
            cursor = channel + 1;
            continue;
        }
        patterns++;
        end = strchr(patterns, ']');
        if (!end) {
            cursor = channel + 1;
            continue;
        }

        pat = patterns;
        while (pat < end && Cache->RuleCount < SENTINEL_MAX_RULES) {
            pat = strchr(pat, '"');
            if (!pat || pat >= end) {
                break;
            }
            pat++;
            {
                PCHAR patEnd = strchr(pat, '"');
                ULONG patLen;
                if (!patEnd || patEnd > end) {
                    break;
                }
                patLen = (ULONG)(patEnd - pat);
                if (patLen > 0 && patLen < SENTINEL_PATTERN_MAX) {
                    i = Cache->RuleCount;
                    RtlZeroMemory(Cache->Rules[i].Pattern, sizeof(Cache->Rules[i].Pattern));
                    RtlCopyMemory(Cache->Rules[i].Pattern, pat, patLen);
                    Cache->Rules[i].Block = TRUE;
                    Cache->RuleCount++;
                }
                pat = patEnd + 1;
            }
        }
        cursor = end + 1;
    }

    ExFreePoolWithTag(buf, SENTINEL_FILTER_TAG);
    return Cache->RuleCount > 0;
}

BOOLEAN SentinelPathMatchesRule(_In_ PCUNICODE_STRING Path, _In_ PCWSTR Pattern)
{
    UNICODE_STRING pat;
    ULONG i;
    ULONG pathLen;
    ULONG patLen;

    if (!Path || !Pattern || Path->Length == 0) {
        return FALSE;
    }

    RtlInitUnicodeString(&pat, Pattern);
    pathLen = Path->Length / sizeof(WCHAR);
    patLen = pat.Length / sizeof(WCHAR);

    if (patLen == 0) {
        return FALSE;
    }

    /* *.ext suffix match */
    if (Pattern[0] == L'*' && patLen > 1) {
        PCWSTR suffix = Pattern + 1;
        ULONG suffixLen = patLen - 1;
        if (pathLen >= suffixLen) {
            for (i = 0; i < suffixLen; i++) {
                if (Path->Buffer[pathLen - suffixLen + i] != suffix[i]) {
                    return FALSE;
                }
            }
            return TRUE;
        }
        return FALSE;
    }

    /* substring match */
    for (i = 0; i + patLen <= pathLen; i++) {
        if (RtlCompareMemory(&Path->Buffer[i], Pattern, pat.Length) == pat.Length) {
            return TRUE;
        }
    }
    return FALSE;
}

BOOLEAN SentinelStrContains(_In_ PCSTR Haystack, _In_ PCSTR Needle)
{
    return (Haystack && Needle && strstr(Haystack, Needle) != NULL);
}
