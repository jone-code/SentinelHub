//! Windows minifilter communication port client (fltuser.dll).

const SENTINEL_POLICY_MAX: usize = 4096;

#[cfg(target_os = "windows")]
mod imp {
    use super::SENTINEL_POLICY_MAX;
    use crate::DriverStatus;
    use serde_json::{json, Value};
    use std::ffi::c_void;
    use std::ptr;

    const SENTINEL_MSG_SET_POLICY: u32 = 1;
    const SENTINEL_MSG_GET_STATUS: u32 = 2;
    const SENTINEL_MSG_DRAIN_EVENTS: u32 = 3;
    const SENTINEL_EVENT_PATH_MAX: usize = 256;
    const HRESULT_S_OK: i32 = 0;

    #[repr(C)]
    struct SentinelEvent {
        type_: u32,
        pid: u32,
        blocked: u32,
        path: [u8; SENTINEL_EVENT_PATH_MAX],
    }

    #[link(name = "fltuser")]
    extern "system" {
        fn FilterConnectCommunicationPort(
            PortName: *const u16,
            Options: u32,
            ConnectionContext: *const c_void,
            SizeOfContext: u16,
            SecurityAttributes: *const c_void,
            Port: *mut *mut c_void,
        ) -> i32;

        fn FilterSendMessage(
            Port: *mut c_void,
            InBuffer: *const c_void,
            InBufferSize: u32,
            OutBuffer: *mut c_void,
            OutBufferSize: u32,
            BytesReturned: *mut u32,
        ) -> i32;

        fn FilterCloseCommunicationPort(Port: *mut c_void) -> i32;
    }

    fn port_name() -> Vec<u16> {
        "\\SentinelHubPort\0".encode_utf16().collect()
    }

    fn connect_port() -> Option<*mut c_void> {
        let mut port: *mut c_void = ptr::null_mut();
        let name = port_name();
        let hr = unsafe {
            FilterConnectCommunicationPort(
                name.as_ptr(),
                0,
                ptr::null(),
                0,
                ptr::null(),
                &mut port,
            )
        };
        if hr != HRESULT_S_OK || port.is_null() {
            None
        } else {
            Some(port)
        }
    }

    fn close_port(port: *mut c_void) {
        if !port.is_null() {
            unsafe {
                let _ = FilterCloseCommunicationPort(port);
            }
        }
    }

    pub fn push_policy(payload: &str) -> bool {
        if payload.len() > SENTINEL_POLICY_MAX {
            return false;
        }
        let Some(port) = connect_port() else {
            return false;
        };

        let mut buf = vec![0u8; 8 + payload.len()];
        buf[0..4].copy_from_slice(&SENTINEL_MSG_SET_POLICY.to_le_bytes());
        buf[4..8].copy_from_slice(&(payload.len() as u32).to_le_bytes());
        buf[8..].copy_from_slice(payload.as_bytes());

        let mut bytes_returned = 0u32;
        let hr = unsafe {
            FilterSendMessage(
                port,
                buf.as_ptr() as *const c_void,
                buf.len() as u32,
                ptr::null_mut(),
                0,
                &mut bytes_returned,
            )
        };
        close_port(port);
        hr == HRESULT_S_OK
    }

    pub fn probe() -> Option<DriverStatus> {
        let port = connect_port()?;
        let mut in_buf = [0u8; 8];
        in_buf[0..4].copy_from_slice(&SENTINEL_MSG_GET_STATUS.to_le_bytes());
        let mut out = 0u32;
        let mut bytes_returned = 0u32;
        let hr = unsafe {
            FilterSendMessage(
                port,
                in_buf.as_ptr() as *const c_void,
                8,
                &mut out as *mut u32 as *mut c_void,
                std::mem::size_of::<u32>() as u32,
                &mut bytes_returned,
            )
        };
        close_port(port);
        if hr != HRESULT_S_OK {
            return None;
        }
        Some(DriverStatus {
            available: true,
            mode: "kernel_minifilter",
            message: format!("Windows minifilter active ({out} rules)"),
            kernel_loaded: true,
            daemon_version: None,
            kernel_version: None,
            capabilities: Some(vec![
                "kernel_policy".into(),
                "file_hook".into(),
                "usb_block".into(),
                "process_block".into(),
                "event_ring".into(),
            ]),
            socket_path: String::new(),
        })
    }

    fn path_from_bytes(path: &[u8]) -> String {
        let end = path.iter().position(|&b| b == 0).unwrap_or(path.len());
        String::from_utf8_lossy(&path[..end]).into_owned()
    }

    pub fn drain_events() -> Vec<Value> {
        let Some(port) = connect_port() else {
            return Vec::new();
        };

        let mut in_buf = [0u8; 8];
        in_buf[0..4].copy_from_slice(&SENTINEL_MSG_DRAIN_EVENTS.to_le_bytes());

        let out_cap = 4 + 64 * std::mem::size_of::<SentinelEvent>();
        let mut out_buf = vec![0u8; out_cap];
        let mut bytes_returned = 0u32;
        let hr = unsafe {
            FilterSendMessage(
                port,
                in_buf.as_ptr() as *const c_void,
                8,
                out_buf.as_mut_ptr() as *mut c_void,
                out_cap as u32,
                &mut bytes_returned,
            )
        };
        close_port(port);
        if hr != HRESULT_S_OK || bytes_returned < 4 {
            return Vec::new();
        }

        let count = u32::from_le_bytes(out_buf[0..4].try_into().unwrap()) as usize;
        let mut events = Vec::with_capacity(count);
        let mut offset = 4usize;
        for _ in 0..count {
            if offset + std::mem::size_of::<SentinelEvent>() > bytes_returned as usize {
                break;
            }
            let raw = unsafe {
                ptr::read_unaligned(out_buf.as_ptr().add(offset) as *const SentinelEvent)
            };
            offset += std::mem::size_of::<SentinelEvent>();
            events.push(json!({
                "type": raw.type_,
                "pid": raw.pid,
                "blocked": raw.blocked != 0,
                "path": path_from_bytes(&raw.path),
            }));
        }
        events
    }
}

#[cfg(target_os = "windows")]
pub use imp::{drain_events, probe, push_policy};
