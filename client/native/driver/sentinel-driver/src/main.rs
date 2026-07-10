//! Userspace driver daemon — Unix socket JSON-line IPC + kernel module bridge.

mod fanotify;
mod kernel;
mod process_block;

use serde::{Deserialize, Serialize};
use std::io::{BufRead, BufReader, Write};
use std::sync::Mutex;
use std::time::Duration;

#[cfg(target_os = "linux")]
static FANOTIFY: Mutex<Option<fanotify::FanotifyWatcher>> = Mutex::new(None);

#[cfg(target_os = "linux")]
static PROCESS_WATCHER: Mutex<Option<process_block::ProcessWatcher>> = Mutex::new(None);

#[derive(Deserialize)]
struct Request {
    cmd: String,
    #[serde(default)]
    policy: Option<String>,
    #[serde(default)]
    limit: Option<usize>,
}

#[derive(Serialize)]
struct StatusResponse {
    ok: bool,
    version: &'static str,
    mode: &'static str,
    kernel_loaded: bool,
    fanotify_active: bool,
    process_block_active: bool,
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
            "kernel module active (v{}, policy {} bytes, events {})",
            st.version, st.policy_len, st.event_count
        );
        return ("kernel_lsm", true, msg);
    }
    (
        "userspace_daemon",
        false,
        "userspace driver daemon running; kernel module not loaded".into(),
    )
}

fn fanotify_active() -> bool {
    #[cfg(target_os = "linux")]
    {
        return FANOTIFY.lock().unwrap().is_some();
    }
    #[allow(unreachable_code)]
    false
}

fn process_block_active() -> bool {
    #[cfg(target_os = "linux")]
    {
        return PROCESS_WATCHER.lock().unwrap().is_some();
    }
    #[allow(unreachable_code)]
    false
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
        caps.push("event_ring");
    }
    if fanotify_active() {
        caps.push("fanotify_active");
    }
    if process_block_active() {
        caps.push("process_block_active");
    }
    caps
}

#[cfg(target_os = "linux")]
fn restart_fanotify(policy: &str) -> Result<(), String> {
    let mut guard = FANOTIFY.lock().unwrap();
    *guard = None;
    match fanotify::FanotifyWatcher::start(policy) {
        Ok(w) => {
            *guard = Some(w);
            Ok(())
        }
        Err(e) => Err(format!("fanotify start failed: {e}")),
    }
}

#[cfg(not(target_os = "linux"))]
fn restart_fanotify(_policy: &str) -> Result<(), String> {
    Err("fanotify requires Linux".into())
}

#[cfg(target_os = "linux")]
fn restart_process_block(policy: &str) -> Result<(), String> {
    let mut guard = PROCESS_WATCHER.lock().unwrap();
    *guard = None;
    match process_block::ProcessWatcher::start(policy) {
        Ok(w) => {
            *guard = Some(w);
            Ok(())
        }
        Err(e) => Err(format!("process_block start failed: {e}")),
    }
}

#[cfg(not(target_os = "linux"))]
fn restart_process_block(_policy: &str) -> Result<(), String> {
    Err("process_block requires Linux".into())
}

fn apply_policy(policy: &str, kernel_loaded: bool) -> Result<serde_json::Value, String> {
    #[cfg(target_os = "linux")]
    if kernel_loaded {
        kernel::set_policy(policy.as_bytes())
            .map_err(|e| format!("kernel policy push failed: {e}"))?;
    }
    let fanotify = restart_fanotify(policy);
    let process_block = restart_process_block(policy);
    Ok(serde_json::json!({
        "ok": true,
        "kernel_loaded": kernel_loaded,
        "policy_bytes": policy.len(),
        "fanotify": fanotify.is_ok(),
        "fanotify_warning": fanotify.err(),
        "process_block": process_block.is_ok(),
        "process_block_warning": process_block.err()
    }))
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
            fanotify_active: fanotify_active(),
            process_block_active: process_block_active(),
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
            match apply_policy(&policy, kernel_loaded) {
                Ok(v) => v.to_string(),
                Err(e) => serde_json::to_string(&ErrorResponse { ok: false, error: e })
                    .unwrap_or_else(|_| "{}".into()),
            }
        }
        "get_events" => {
            #[cfg(target_os = "linux")]
            {
                let limit = req.limit.unwrap_or(20);
                let mut file_events = Vec::new();
                let mut process_events = Vec::new();
                if let Some(w) = FANOTIFY.lock().unwrap().as_ref() {
                    file_events = w.recent_events(limit);
                }
                if let Some(w) = PROCESS_WATCHER.lock().unwrap().as_ref() {
                    process_events = w.recent_events(limit);
                }
                return serde_json::json!({
                    "ok": true,
                    "file_events": file_events,
                    "process_events": process_events
                })
                .to_string();
            }
            #[cfg(not(target_os = "linux"))]
            {
                serde_json::json!({
                    "ok": true,
                    "file_events": [],
                    "process_events": []
                })
                .to_string()
            }
        }
        "drain_kernel_events" => {
            #[cfg(target_os = "linux")]
            {
                let mut out = Vec::new();
                loop {
                    match kernel::get_event() {
                        Ok(ev) => {
                            let path = String::from_utf8_lossy(
                                &ev.path[..ev.path.iter().position(|&b| b == 0).unwrap_or(ev.path.len())],
                            )
                            .into_owned();
                            out.push(serde_json::json!({
                                "type": ev.type_,
                                "pid": ev.pid,
                                "blocked": ev.blocked != 0,
                                "path": path
                            }));
                        }
                        Err(_) => break,
                    }
                }
                serde_json::json!({ "ok": true, "events": out }).to_string()
            }
            #[cfg(not(target_os = "linux"))]
            {
                serde_json::json!({ "ok": true, "events": [] }).to_string()
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

    #[cfg(target_os = "linux")]
    if kernel {
        if let Ok(policy) = kernel::get_policy() {
            if let Ok(text) = String::from_utf8(policy) {
                let _ = restart_fanotify(&text);
                let _ = restart_process_block(&text);
            }
        }
    }

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
            fanotify_active: fanotify_active(),
            process_block_active: process_block_active(),
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
