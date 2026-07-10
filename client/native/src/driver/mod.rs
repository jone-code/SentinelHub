//! Driver IPC — kernel module and userspace daemon.

#[cfg(target_os = "linux")]
mod kernel;

use serde::Serialize;
use serde_json::Value;
use std::time::Duration;

#[derive(Serialize, Clone, Default)]
pub struct DriverEventBatch {
    pub file_events: Vec<Value>,
    pub process_events: Vec<Value>,
    pub kernel_events: Vec<Value>,
}

#[derive(Serialize, Clone)]
pub struct DriverStatus {
    pub available: bool,
    pub mode: &'static str,
    pub message: String,
    pub kernel_loaded: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub daemon_version: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub kernel_version: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub capabilities: Option<Vec<String>>,
    pub socket_path: String,
}

pub fn status() -> DriverStatus {
    let socket_path = default_socket_path();

    #[cfg(target_os = "linux")]
    if let Some(kst) = kernel::probe() {
        return DriverStatus {
            available: true,
            mode: "kernel_lsm",
            message: "kernel module loaded (/dev/sentinelhub)".into(),
            kernel_loaded: true,
            daemon_version: None,
            kernel_version: Some(kst.version),
            capabilities: Some(vec![
                "kernel_policy".into(),
                "file_hook".into(),
                "event_ring".into(),
                "process_block".into(),
                "usb_unmount".into(),
                "sensitive_path_scan".into(),
            ]),
            socket_path,
        };
    }

    #[cfg(unix)]
    {
        if let Some(daemon) = probe_daemon(&socket_path) {
            return DriverStatus {
                available: true,
                mode: "userspace_daemon",
                message: daemon.message,
                kernel_loaded: false,
                daemon_version: Some(daemon.version),
                kernel_version: None,
                capabilities: Some(daemon.capabilities),
                socket_path,
            };
        }
    }

    DriverStatus {
        available: false,
        mode: "stub",
        message: "no kernel module or driver daemon; using userspace enforcers only".into(),
        kernel_loaded: false,
        daemon_version: None,
        kernel_version: None,
        capabilities: None,
        socket_path,
    }
}

/// Push policy JSON to kernel module and/or driver daemon.
pub fn push_policy(payload: &str) -> bool {
    #[cfg(target_os = "linux")]
    {
        let mut ok = false;
        if kernel::set_policy(payload.as_bytes()).is_ok() {
            ok = true;
        }
        if push_policy_to_daemon(payload) {
            ok = true;
        }
        return ok;
    }
    #[allow(unreachable_code)]
    false
}

/// Fetch daemon watcher events and drain kernel ring buffer.
pub fn collect_events(limit: usize) -> DriverEventBatch {
    #[cfg(unix)]
    {
        let mut batch = DriverEventBatch::default();
        if let Some(resp) = daemon_request(&serde_json::json!({
            "cmd": "get_events",
            "limit": limit
        })) {
            if let Some(arr) = resp.get("file_events").and_then(|v| v.as_array()) {
                batch.file_events = arr.clone();
            }
            if let Some(arr) = resp.get("process_events").and_then(|v| v.as_array()) {
                batch.process_events = arr.clone();
            }
        }
        if let Some(resp) = daemon_request(&serde_json::json!({
            "cmd": "drain_kernel_events"
        })) {
            if let Some(arr) = resp.get("events").and_then(|v| v.as_array()) {
                batch.kernel_events = arr.clone();
            }
        }
        return batch;
    }
    #[allow(unreachable_code)]
    DriverEventBatch::default()
}

#[cfg(unix)]
fn daemon_request(request: &Value) -> Option<Value> {
    use std::io::{Read, Write};
    use std::os::unix::net::UnixStream;

    let path = default_socket_path();
    let mut stream = UnixStream::connect(&path).ok()?;
    stream
        .set_read_timeout(Some(Duration::from_millis(1500)))
        .ok();
    stream
        .set_write_timeout(Some(Duration::from_millis(1500)))
        .ok();

    let line = format!("{request}\n");
    stream.write_all(line.as_bytes()).ok()?;
    stream.flush().ok()?;

    let mut buf = vec![0u8; 65536];
    let n = stream.read(&mut buf).ok()?;
    if n == 0 {
        return None;
    }
    let text = String::from_utf8_lossy(&buf[..n]);
    let response_line = text.lines().next()?;
    let value: Value = serde_json::from_str(response_line).ok()?;
    if !value.get("ok").and_then(|v| v.as_bool()).unwrap_or(false) {
        return None;
    }
    Some(value)
}

#[cfg(unix)]
fn push_policy_to_daemon(payload: &str) -> bool {
    daemon_request(&serde_json::json!({
        "cmd": "set_policy",
        "policy": payload
    }))
    .is_some()
}

#[cfg(not(unix))]
fn push_policy_to_daemon(_payload: &str) -> bool {
    false
}

fn default_socket_path() -> String {
    std::env::var("SENTINEL_DRIVER_SOCKET")
        .unwrap_or_else(|_| "/tmp/sentinel-driver.sock".into())
}

#[cfg(unix)]
struct DaemonInfo {
    version: String,
    message: String,
    capabilities: Vec<String>,
}

#[cfg(unix)]
fn probe_daemon(socket_path: &str) -> Option<DaemonInfo> {
    use std::io::{Read, Write};
    use std::os::unix::net::UnixStream;

    let mut stream = UnixStream::connect(socket_path).ok()?;
    stream
        .set_read_timeout(Some(Duration::from_millis(500)))
        .ok()?;
    stream
        .set_write_timeout(Some(Duration::from_millis(500)))
        .ok()?;

    let request = r#"{"cmd":"status"}"#;
    stream.write_all(format!("{request}\n").as_bytes()).ok()?;
    stream.flush().ok()?;

    let mut buf = vec![0u8; 4096];
    let n = stream.read(&mut buf).ok()?;
    if n == 0 {
        return None;
    }
    let text = String::from_utf8_lossy(&buf[..n]);
    let line = text.lines().next()?;
    let value: serde_json::Value = serde_json::from_str(line).ok()?;
    if !value.get("ok").and_then(|v| v.as_bool()).unwrap_or(false) {
        return None;
    }
    Some(DaemonInfo {
        version: value
            .get("version")
            .and_then(|v| v.as_str())
            .unwrap_or("unknown")
            .into(),
        message: value
            .get("message")
            .and_then(|v| v.as_str())
            .unwrap_or("daemon connected")
            .into(),
        capabilities: value
            .get("capabilities")
            .and_then(|v| v.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|c| c.as_str().map(String::from))
                    .collect()
            })
            .unwrap_or_default(),
    })
}
