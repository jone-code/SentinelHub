//! Userspace driver daemon — Unix socket JSON-line IPC + kernel module bridge.

mod kernel;

use serde::{Deserialize, Serialize};
use std::io::{BufRead, BufReader, Write};
use std::time::Duration;

#[derive(Deserialize)]
struct Request {
    cmd: String,
    #[serde(default)]
    policy: Option<String>,
}

#[derive(Serialize)]
struct StatusResponse {
    ok: bool,
    version: &'static str,
    mode: &'static str,
    kernel_loaded: bool,
    capabilities: Vec<&'static str>,
    message: String,
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

fn driver_mode() -> (&'static str, bool, String) {
    #[cfg(target_os = "linux")]
    if let Some(st) = kernel::probe() {
        let msg = format!(
            "kernel module active (version {}, policy {} bytes)",
            st.version, st.policy_len
        );
        return ("kernel_lsm", true, msg);
    }
    (
        "userspace_daemon",
        false,
        "userspace driver daemon running; kernel module not loaded".into(),
    )
}

fn capabilities(kernel: bool) -> Vec<&'static str> {
    let mut caps = vec![
        "process_block",
        "usb_unmount",
        "sensitive_path_scan",
        "health_probe",
    ];
    if kernel {
        caps.push("kernel_policy");
        caps.push("file_hook");
    }
    caps
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

    let (mode, kernel_loaded, message) = driver_mode();

    let response = match req.cmd.as_str() {
        "ping" => serde_json::json!({ "ok": true, "pong": true }).to_string(),
        "status" => serde_json::to_string(&StatusResponse {
            ok: true,
            version: env!("CARGO_PKG_VERSION"),
            mode,
            kernel_loaded,
            capabilities: capabilities(kernel_loaded),
            message,
        })
        .unwrap_or_else(|_| "{}".into()),
        "capabilities" => serde_json::json!({
            "ok": true,
            "capabilities": capabilities(kernel_loaded)
        })
        .to_string(),
        "set_policy" => {
            let Some(policy) = req.policy.filter(|p| !p.is_empty()) else {
                return serde_json::to_string(&ErrorResponse {
                    ok: false,
                    error: "policy required".into(),
                })
                .unwrap_or_else(|_| "{}".into());
            };
            #[cfg(target_os = "linux")]
            {
                match kernel::set_policy(policy.as_bytes()) {
                    Ok(()) => serde_json::json!({
                        "ok": true,
                        "kernel_loaded": kernel_loaded,
                        "policy_bytes": policy.len()
                    })
                    .to_string(),
                    Err(e) => serde_json::to_string(&ErrorResponse {
                        ok: false,
                        error: format!("kernel policy push failed: {e}"),
                    })
                    .unwrap_or_else(|_| "{}".into()),
                }
            }
            #[cfg(not(target_os = "linux"))]
            {
                serde_json::to_string(&ErrorResponse {
                    ok: false,
                    error: "set_policy requires Linux kernel module".into(),
                })
                .unwrap_or_else(|_| "{}".into())
            }
        }
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

    let (mode, kernel, _) = driver_mode();
    eprintln!("[sentinel-driver] listening on {path} (mode={mode}, kernel={kernel})");

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
        let (mode, kernel_loaded, message) = driver_mode();
        let status = StatusResponse {
            ok: true,
            version: env!("CARGO_PKG_VERSION"),
            mode,
            kernel_loaded,
            capabilities: capabilities(kernel_loaded),
            message,
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
