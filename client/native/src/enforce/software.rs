use serde::Serialize;
use serde_json::Value;
use std::collections::HashSet;
use std::fs;
use std::path::Path;

#[derive(Serialize)]
pub struct EnforceResult {
    pub checked_at: String,
    pub violations: Vec<Violation>,
}

#[derive(Serialize)]
pub struct Violation {
    pub process: String,
    pub matched_rule: String,
    pub action: String,
}

pub fn run(policy_path: &Path) -> Result<EnforceResult, String> {
    let raw = fs::read_to_string(policy_path).map_err(|e| e.to_string())?;
    let policy: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;

    let blacklist = extract_blacklist(&policy);
    let action = extract_action(&policy);
    let processes = list_process_names()?;

    let mut violations = Vec::new();
    for proc in processes {
        if let Some(rule) = match_blacklist(&proc, &blacklist) {
            violations.push(Violation {
                process: proc.clone(),
                matched_rule: rule,
                action: action.clone(),
            });
        }
    }

    Ok(EnforceResult {
        checked_at: chrono_now(),
        violations,
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

fn list_process_names() -> Result<Vec<String>, String> {
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

fn list_linux_processes() -> Result<Vec<String>, String> {
    let mut names = HashSet::new();
    let entries = fs::read_dir("/proc").map_err(|e| e.to_string())?;
    for entry in entries.flatten() {
        let pid = entry.file_name().to_string_lossy().to_string();
        if !pid.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        if let Ok(comm) = fs::read_to_string(format!("/proc/{pid}/comm")) {
            names.insert(comm.trim().to_string());
        }
    }
    Ok(names.into_iter().collect())
}

fn list_macos_processes() -> Result<Vec<String>, String> {
    let output = std::process::Command::new("ps")
        .args(["-eo", "comm="])
        .output()
        .map_err(|e| e.to_string())?;
    let text = String::from_utf8_lossy(&output.stdout);
    let names: HashSet<String> = text
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty())
        .collect();
    Ok(names.into_iter().collect())
}

fn list_windows_processes() -> Result<Vec<String>, String> {
    let output = std::process::Command::new("tasklist")
        .args(["/FO", "CSV", "/NH"])
        .output()
        .map_err(|e| e.to_string())?;
    let text = String::from_utf8_lossy(&output.stdout);
    let mut names = HashSet::new();
    for line in text.lines() {
        let part = line.split(',').next().unwrap_or("").trim_matches('"');
        if !part.is_empty() {
            names.insert(part.to_string());
        }
    }
    Ok(names.into_iter().collect())
}

fn chrono_now() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("{secs}")
}
