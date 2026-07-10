/* SentinelHub Windows minifilter — phase 2: communication port skeleton. */

#include "SentinelFilter.h"

PFLT_FILTER g_sentinel_filter = NULL;
PFLT_PORT g_sentinel_server_port = NULL;
PFLT_PORT g_sentinel_client_port = NULL;

#define SENTINEL_PORT_NAME L"\\SentinelHubPort"

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
    UNREFERENCED_PARAMETER(PortCookie);
    UNREFERENCED_PARAMETER(InputBuffer);
    UNREFERENCED_PARAMETER(InputBufferLength);
    /* Phase 3: parse policy JSON from InputBuffer, cache in kernel */
    if (OutputBuffer && OutputBufferLength >= sizeof(ULONG)) {
        *(PULONG)OutputBuffer = 1;
        *ReturnOutputBufferLength = sizeof(ULONG);
    } else {
        *ReturnOutputBufferLength = 0;
    }
    return STATUS_SUCCESS;
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
    UNREFERENCED_PARAMETER(Data);
    UNREFERENCED_PARAMETER(FltObjects);
    UNREFERENCED_PARAMETER(CompletionContext);
    /* Phase 3: consult cached policy; deny with STATUS_ACCESS_DENIED when matched */
    return FLT_PREOP_SUCCESS_WITH_CALLBACK;
}
