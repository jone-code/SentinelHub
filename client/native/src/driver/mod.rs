//! Driver IPC — probe sentinel-driver daemon over Unix socket.

use serde::Serialize;
use std::io::{Read, Write};
use std::time::Duration;

#[derive(Serialize, Clone)]
pub struct DriverStatus {
    pub available: bool,
    pub mode: &'static str,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub daemon_version: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub capabilities: Option<Vec<String>>,
    pub socket_path: String,
}

pub fn status() -> DriverStatus {
    let socket_path = default_socket_path();
    #[cfg(unix)]
    {
        if let Some(daemon) = probe_daemon(&socket_path) {
            return DriverStatus {
                available: true,
                mode: "userspace_daemon",
                message: daemon.message,
                daemon_version: Some(daemon.version),
                capabilities: Some(daemon.capabilities),
                socket_path,
            };
        }
    }
    DriverStatus {
        available: false,
        mode: "stub",
        message: "driver daemon not running; start sentinel-driver or use userspace enforcers".into(),
        daemon_version: None,
        capabilities: None,
        socket_path,
    }
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
