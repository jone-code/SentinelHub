use serde::Serialize;
use serde_json::Value;
use std::collections::HashMap;
use std::fs;
use std::path::Path;

#[derive(Clone)]
struct ProcessInfo {
    pid: u32,
    name: String,
}

#[derive(Serialize)]
pub struct EnforceResult {
    pub checked_at: String,
    pub driver_assisted: bool,
    pub violations: Vec<Violation>,
}

#[derive(Serialize)]
pub struct Violation {
    pub process: String,
    pub matched_rule: String,
    pub action: String,
    pub blocked: bool,
    pub terminated_pids: Vec<u32>,
}

pub fn run(policy_path: &Path) -> Result<EnforceResult, String> {
    let raw = fs::read_to_string(policy_path).map_err(|e| e.to_string())?;
    let driver = crate::driver::status();
    if driver.available {
        let _ = crate::driver::push_policy(&raw);
    }
    let policy: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;

    let blacklist = extract_blacklist(&policy);
    let action = extract_action(&policy);
    let processes = list_processes()?;

    let mut violations: HashMap<String, Violation> = HashMap::new();
    for proc in processes {
        let Some(rule) = match_blacklist(&proc.name, &blacklist) else {
            continue;
        };
        let key = normalize_name(&proc.name);
        let entry = violations.entry(key).or_insert_with(|| Violation {
            process: proc.name.clone(),
            matched_rule: rule.clone(),
            action: action.clone(),
            blocked: false,
            terminated_pids: Vec::new(),
        });
        if action == "block" {
            if terminate_pid(proc.pid) {
                entry.blocked = true;
                entry.terminated_pids.push(proc.pid);
            }
        }
    }

    Ok(EnforceResult {
        checked_at: chrono_now(),
        driver_assisted: driver.available,
        violations: violations.into_values().collect(),
    })
}

fn extract_blacklist(policy: &Value) -> Vec<String> {
    policy
        .pointer("/rules/software/config/blacklist")
        .or_else(|| policy.pointer("/software/config/blacklist"))
        .and_then(|v| v.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(|s| s.to_lowercase()))
                .collect()
        })
        .unwrap_or_default()
}

fn extract_action(policy: &Value) -> String {
    policy
        .pointer("/rules/software/config/action")
        .or_else(|| policy.pointer("/software/config/action"))
        .and_then(|v| v.as_str())
        .unwrap_or("alert")
        .to_string()
}

fn match_blacklist(process: &str, blacklist: &[String]) -> Option<String> {
    let norm = normalize_name(process);
    for rule in blacklist {
        let r = normalize_name(rule);
        if norm == r {
            return Some(rule.clone());
        }
    }
    None
}

fn normalize_name(name: &str) -> String {
    name.trim()
        .to_lowercase()
        .trim_end_matches(".exe")
        .to_string()
}

fn list_processes() -> Result<Vec<ProcessInfo>, String> {
    if cfg!(target_os = "linux") {
        return list_linux_processes();
    }
    if cfg!(target_os = "macos") {
        return list_macos_processes();
    }
    if cfg!(target_os = "windows") {
        return list_windows_processes();
    }
    Err("unsupported platform".into())
}

fn list_linux_processes() -> Result<Vec<ProcessInfo>, String> {
    let mut processes = Vec::new();
    let entries = fs::read_dir("/proc").map_err(|e| e.to_string())?;
    for entry in entries.flatten() {
        let pid_str = entry.file_name().to_string_lossy().to_string();
        if !pid_str.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        let pid: u32 = pid_str.parse().unwrap_or(0);
        if pid == 0 {
            continue;
        }
        if let Ok(comm) = fs::read_to_string(format!("/proc/{pid}/comm")) {
            processes.push(ProcessInfo {
                pid,
                name: comm.trim().to_string(),
            });
        }
    }
    Ok(processes)
}

fn list_macos_processes() -> Result<Vec<ProcessInfo>, String> {
    let output = std::process::Command::new("ps")
        .args(["-eo", "pid,comm="])
        .output()
        .map_err(|e| e.to_string())?;
    let text = String::from_utf8_lossy(&output.stdout);
    let mut processes = Vec::new();
    for line in text.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let mut parts = line.split_whitespace();
        let Some(pid_str) = parts.next() else {
            continue;
        };
        let Ok(pid) = pid_str.parse::<u32>() else {
            continue;
        };
        let name = parts.collect::<Vec<_>>().join(" ");
        if !name.is_empty() {
            processes.push(ProcessInfo { pid, name });
        }
    }
    Ok(processes)
}

fn list_windows_processes() -> Result<Vec<ProcessInfo>, String> {
    let output = std::process::Command::new("tasklist")
        .args(["/FO", "CSV", "/NH"])
        .output()
        .map_err(|e| e.to_string())?;
    let text = String::from_utf8_lossy(&output.stdout);
    let mut processes = Vec::new();
    for line in text.lines() {
        let cols: Vec<&str> = line.split(',').map(|s| s.trim_matches('"')).collect();
        if cols.len() < 2 {
            continue;
        }
        let name = cols[0].to_string();
        let Ok(pid) = cols[1].parse::<u32>() else {
            continue;
        };
        if !name.is_empty() {
            processes.push(ProcessInfo { pid, name });
        }
    }
    Ok(processes)
}

fn terminate_pid(pid: u32) -> bool {
    if pid <= 1 {
        return false;
    }
    if cfg!(unix) {
        let status = std::process::Command::new("kill")
            .args(["-TERM", &pid.to_string()])
            .status();
        return status.map(|s| s.success()).unwrap_or(false);
    }
    if cfg!(windows) {
        let status = std::process::Command::new("taskkill")
            .args(["/PID", &pid.to_string(), "/F"])
            .status();
        return status.map(|s| s.success()).unwrap_or(false);
    }
    false
}

fn chrono_now() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("{secs}")
}
