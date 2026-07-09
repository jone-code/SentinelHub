//! Userspace driver daemon — Unix socket JSON-line IPC.

use serde::{Deserialize, Serialize};
use std::io::{BufRead, BufReader, Write};
use std::time::Duration;

#[derive(Deserialize)]
struct Request {
    cmd: String,
}

#[derive(Serialize)]
struct StatusResponse {
    ok: bool,
    version: &'static str,
    mode: &'static str,
    capabilities: Vec<&'static str>,
    message: &'static str,
}

#[derive(Serialize)]
struct ErrorResponse {
    ok: bool,
    error: String,
}

fn socket_path() -> String {
    std::env::var("SENTINEL_DRIVER_SOCKET")
        .unwrap_or_else(|_| "/tmp/sentinel-driver.sock".into())
}

fn handle_request(line: &str) -> String {
    let req: Request = match serde_json::from_str(line) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::to_string(&ErrorResponse {
                ok: false,
                error: format!("invalid request: {e}"),
            })
            .unwrap_or_else(|_| r#"{"ok":false,"error":"serialize failed"}"#.into());
        }
    };

    let response = match req.cmd.as_str() {
        "ping" => serde_json::json!({ "ok": true, "pong": true }).to_string(),
        "status" => serde_json::to_string(&StatusResponse {
            ok: true,
            version: env!("CARGO_PKG_VERSION"),
            mode: "userspace_daemon",
            capabilities: vec![
                "process_block",
                "usb_unmount",
                "sensitive_path_scan",
                "health_probe",
            ],
            message: "userspace driver daemon running; kernel hooks not loaded",
        })
        .unwrap_or_else(|_| "{}".into()),
        "capabilities" => serde_json::json!({
            "ok": true,
            "capabilities": [
                "process_block",
                "usb_unmount",
                "sensitive_path_scan",
                "health_probe"
            ]
        })
        .to_string(),
        other => serde_json::to_string(&ErrorResponse {
            ok: false,
            error: format!("unknown cmd: {other}"),
        })
        .unwrap_or_else(|_| "{}".into()),
    };

    response
}

#[cfg(unix)]
fn run_daemon() -> Result<(), String> {
    use std::fs;
    use std::os::unix::net::UnixListener;

    let path = socket_path();
    let _ = fs::remove_file(&path);
    let listener = UnixListener::bind(&path).map_err(|e| format!("bind {path}: {e}"))?;
    listener
        .set_nonblocking(true)
        .map_err(|e| format!("set_nonblocking: {e}"))?;

    eprintln!("[sentinel-driver] listening on {path}");

    loop {
        match listener.accept() {
            Ok((stream, _)) => {
                let mut reader = BufReader::new(
                    stream
                        .try_clone()
                        .map_err(|e| format!("clone stream: {e}"))?,
                );
                let mut line = String::new();
                if reader.read_line(&mut line).is_err() {
                    continue;
                }
                let response = format!("{}\n", handle_request(line.trim()));
                let mut stream = stream;
                let _ = stream.set_nonblocking(false);
                let _ = stream.write_all(response.as_bytes());
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                std::thread::sleep(Duration::from_millis(50));
            }
            Err(e) => return Err(format!("accept: {e}")),
        }
    }
}

#[cfg(not(unix))]
fn run_daemon() -> Result<(), String> {
    Err("sentinel-driver daemon requires Unix (Linux/macOS)".into())
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() >= 2 && args[1] == "status" {
        let json = args.iter().any(|a| a == "--json");
        let status = StatusResponse {
            ok: true,
            version: env!("CARGO_PKG_VERSION"),
            mode: "userspace_daemon",
            capabilities: vec![
                "process_block",
                "usb_unmount",
                "sensitive_path_scan",
                "health_probe",
            ],
            message: "daemon binary ready",
        };
        if json {
            println!("{}", serde_json::to_string(&status).unwrap_or_else(|_| "{}".into()));
        }
        return;
    }

    if let Err(err) = run_daemon() {
        eprintln!("[sentinel-driver] {err}");
        std::process::exit(1);
    }
}
