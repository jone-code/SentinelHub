/* SentinelHub Windows minifilter — phase 1 skeleton.
 * Registers pre-create callback; policy enforcement wired in phase 2.
 */

#include "SentinelFilter.h"

PFLT_FILTER g_sentinel_filter = NULL;

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

NTSTATUS DriverEntry(
    _In_ PDRIVER_OBJECT DriverObject,
    _In_ PUNICODE_STRING RegistryPath)
{
    UNREFERENCED_PARAMETER(RegistryPath);
    return FltRegisterFilter(DriverObject, &filter_registration, &g_sentinel_filter);
}

NTSTATUS SentinelUnload(_In_ FLT_FILTER_UNLOAD_FLAGS Flags)
{
    UNREFERENCED_PARAMETER(Flags);
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
    /* Phase 2: consult kernel policy cache and return FLT_PREOP_COMPLETE + STATUS_ACCESS_DENIED */
    return FLT_PREOP_SUCCESS_WITH_CALLBACK;
}
