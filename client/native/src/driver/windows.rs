//! Windows minifilter communication port client (fltuser.dll).

const SENTINEL_POLICY_MAX: usize = 4096;

#[cfg(target_os = "windows")]
mod imp {
    use super::SENTINEL_POLICY_MAX;
    use crate::DriverStatus;
    use std::ffi::c_void;
    use std::ptr;

    const SENTINEL_MSG_SET_POLICY: u32 = 1;
    const SENTINEL_MSG_GET_STATUS: u32 = 2;
    const HRESULT_S_OK: i32 = 0;

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
            ]),
            socket_path: String::new(),
        })
    }
}

#[cfg(target_os = "windows")]
pub use imp::{probe, push_policy};
