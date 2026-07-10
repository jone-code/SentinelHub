use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use std::fs;
use std::path::Path;

#[derive(Deserialize)]
struct DlpRule {
    id: String,
    name: String,
    channel: String,
    action: String,
    patterns: Option<Vec<String>>,
}

#[derive(Serialize)]
pub struct DlpResult {
    pub checked_at: String,
    pub driver_assisted: bool,
    pub violations: Vec<DlpViolation>,
}

#[derive(Serialize)]
pub struct DlpEvidenceFile {
    pub filename: String,
    pub sha256: String,
    pub content_base64: String,
    pub size_bytes: usize,
}

#[derive(Serialize)]
pub struct DlpViolation {
    pub rule_id: String,
    pub rule_name: String,
    pub channel: String,
    pub action: String,
    pub detail: String,
    pub blocked: bool,
    pub evidence_files: Vec<DlpEvidenceFile>,
}

pub fn run(rules_path: &Path) -> Result<DlpResult, String> {
    let raw = fs::read_to_string(rules_path).map_err(|e| e.to_string())?;
    let driver = crate::driver::status();
    if driver.available {
        let _ = crate::driver::push_policy(&raw);
    }
    let rules = load_rules_from_str(&raw)?;
    let mut violations = Vec::new();

    for rule in rules {
        match rule.channel.as_str() {
            "usb" => {
                let devices = list_usb_mounts()?;
                if !devices.is_empty() {
                    let blocked = rule.action == "block" && block_usb(&devices);
                    violations.push(DlpViolation {
                        rule_id: rule.id.clone(),
                        rule_name: rule.name.clone(),
                        channel: rule.channel.clone(),
                        action: rule.action.clone(),
                        detail: format!("检测到 USB/可移动存储: {}", devices.join(", ")),
                        blocked,
                        evidence_files: vec![],
                    });
                }
            }
            "sensitive_path" => {
                let scan = scan_sensitive_files(rule.patterns.as_deref().unwrap_or(&[]));
                if let Some((detail, evidence)) = scan {
                    violations.push(DlpViolation {
                        rule_id: rule.id.clone(),
                        rule_name: rule.name.clone(),
                        channel: rule.channel.clone(),
                        action: rule.action.clone(),
                        detail,
                        blocked: false,
                        evidence_files: evidence,
                    });
                }
            }
            _ => {}
        }
    }

    Ok(DlpResult {
        checked_at: now_epoch(),
        driver_assisted: driver.available,
        violations,
    })
}

fn load_rules_from_str(raw: &str) -> Result<Vec<DlpRule>, String> {
    let value: Value = serde_json::from_str(raw).map_err(|e| e.to_string())?;
    if let Some(arr) = value.as_array() {
        return serde_json::from_value(Value::Array(arr.clone())).map_err(|e| e.to_string());
    }
    if let Some(rules) = value.get("rules") {
        return serde_json::from_value(rules.clone()).map_err(|e| e.to_string());
    }
    Err("invalid dlp rules format".into())
}

fn list_usb_mounts() -> Result<Vec<String>, String> {
    if cfg!(target_os = "linux") {
        return list_linux_usb_mounts();
    }
    Ok(vec![])
}

#[cfg(target_os = "linux")]
fn list_linux_usb_mounts() -> Result<Vec<String>, String> {
    let output = std::process::Command::new("lsblk")
        .args(["-o", "NAME,RM,TYPE,MOUNTPOINT", "-n", "-r"])
        .output()
        .map_err(|e| e.to_string())?;
    let text = String::from_utf8_lossy(&output.stdout);
    let mut mounts = Vec::new();
    for line in text.lines() {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() < 4 {
            continue;
        }
        let removable = parts.get(1).copied().unwrap_or("0") == "1";
        let mount = parts.last().copied().unwrap_or("");
        if removable && !mount.is_empty() && mount.starts_with('/') {
            mounts.push(mount.to_string());
        }
    }
    if mounts.is_empty() {
        let proc = fs::read_to_string("/proc/mounts").unwrap_or_default();
        for line in proc.lines() {
            let cols: Vec<&str> = line.split_whitespace().collect();
            if cols.len() >= 2 {
                let mount = cols[1];
                if mount.contains("/media/") || mount.contains("/mnt/") {
                    mounts.push(mount.to_string());
                }
            }
        }
    }
    Ok(mounts)
}

fn block_usb(mounts: &[String]) -> bool {
    if !cfg!(target_os = "linux") {
        return false;
    }
    let mut blocked = false;
    for mount in mounts {
        let status = std::process::Command::new("umount")
            .arg(mount)
            .status();
        if status.map(|s| s.success()).unwrap_or(false) {
            blocked = true;
        }
    }
    blocked
}

fn scan_sensitive_files(patterns: &[String]) -> Option<(String, Vec<DlpEvidenceFile>)> {
    if patterns.is_empty() {
        return None;
    }
    let home = std::env::var("HOME").ok()?;
    let downloads = format!("{home}/Downloads");
    let mut hits = Vec::new();
    let mut evidence = Vec::new();
    const MAX_BYTES: usize = 65_536;
    for pattern in patterns {
        let ext = pattern.trim_start_matches('*');
        if ext.is_empty() {
            continue;
        }
        if let Ok(entries) = fs::read_dir(&downloads) {
            for entry in entries.flatten() {
                let path = entry.path();
                let name = entry.file_name().to_string_lossy().to_string();
                if !name.ends_with(ext) {
                    continue;
                }
                hits.push(name.clone());
                if let Ok(data) = fs::read(&path) {
                    if data.len() <= MAX_BYTES {
                        evidence.push(DlpEvidenceFile {
                            filename: name,
                            sha256: sha256_hex(&data),
                            content_base64: base64_encode(&data),
                            size_bytes: data.len(),
                        });
                    }
                }
            }
        }
    }
    if hits.is_empty() {
        None
    } else {
        Some((
            format!("Downloads 目录发现敏感文件: {}", hits.join(", ")),
            evidence,
        ))
    }
}

fn sha256_hex(data: &[u8]) -> String {
    use sha2::{Digest, Sha256};
    let hash = Sha256::digest(data);
    format!("sha256:{:x}", hash)
}

fn base64_encode(data: &[u8]) -> String {
    use base64::{engine::general_purpose::STANDARD, Engine as _};
    STANDARD.encode(data)
}

fn now_epoch() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs().to_string())
        .unwrap_or_else(|_| "0".into())
}
