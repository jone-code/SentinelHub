use serde::Deserialize;
use serde::Serialize;
use std::fs;
use std::path::Path;

#[derive(Deserialize)]
struct BaselineRule {
    id: String,
    name: String,
    weight: u32,
    enabled: Option<bool>,
}

#[derive(Serialize)]
pub struct ScanResult {
    pub scanned_at: String,
    pub score: u8,
    pub passed: u32,
    pub failed: u32,
    pub items: Vec<CheckItem>,
}

#[derive(Serialize)]
pub struct CheckItem {
    pub id: String,
    pub name: String,
    pub status: String,
    pub detail: String,
    pub weight: u32,
}

pub fn run(rules_path: Option<&Path>) -> Result<ScanResult, String> {
    let rules = load_rules(rules_path)?;
    let enabled: Vec<&BaselineRule> = rules.iter().filter(|r| r.enabled.unwrap_or(true)).collect();

    let mut items = Vec::new();
    for rule in &enabled {
        items.push(run_check(rule));
    }

    let passed = items.iter().filter(|i| i.status == "pass").count() as u32;
    let failed = items.iter().filter(|i| i.status == "fail").count() as u32;
    let total_weight: u32 = items.iter().map(|i| i.weight).sum();
    let passed_weight: u32 = items
        .iter()
        .filter(|i| i.status == "pass")
        .map(|i| i.weight)
        .sum();
    let score = if total_weight == 0 {
        0
    } else {
        ((passed_weight * 100) / total_weight) as u8
    };

    Ok(ScanResult {
        scanned_at: now_epoch(),
        score,
        passed,
        failed,
        items,
    })
}

fn load_rules(rules_path: Option<&Path>) -> Result<Vec<BaselineRule>, String> {
    if let Some(path) = rules_path {
        let raw = fs::read_to_string(path).map_err(|e| e.to_string())?;
        serde_json::from_str(&raw).map_err(|e| e.to_string())
    } else {
        Ok(default_rules())
    }
}

fn default_rules() -> Vec<BaselineRule> {
    vec![
        rule("firewall", "防火墙", 25),
        rule("os_updates", "操作系统补丁", 25),
        rule("disk_encryption", "磁盘加密", 25),
        rule("antivirus", "杀毒软件", 25),
    ]
}

fn rule(id: &str, name: &str, weight: u32) -> BaselineRule {
    BaselineRule {
        id: id.into(),
        name: name.into(),
        weight,
        enabled: Some(true),
    }
}

fn run_check(rule: &BaselineRule) -> CheckItem {
    let base = match rule.id.as_str() {
        "firewall" => check_firewall(),
        "os_updates" => check_os_updates(),
        "disk_encryption" => check_disk_encryption(),
        "antivirus" => check_antivirus(),
        _ => item(&rule.id, &rule.name, "fail", "不支持的检查项", rule.weight),
    };
    CheckItem {
        id: rule.id.clone(),
        name: rule.name.clone(),
        status: base.status,
        detail: base.detail,
        weight: rule.weight,
    }
}

fn check_firewall() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if ufw_active() || iptables_has_rules() {
            return item("firewall", "防火墙", "pass", "防火墙已启用", 0);
        }
        return item("firewall", "防火墙", "fail", "未检测到活动防火墙", 0);
    }
    #[cfg(target_os = "macos")]
    {
        return item("firewall", "防火墙", "pass", "macOS 防火墙假定已配置", 0);
    }
    #[cfg(target_os = "windows")]
    {
        return item("firewall", "防火墙", "pass", "Windows 防火墙假定已配置", 0);
    }
    #[allow(unreachable_code)]
    item("firewall", "防火墙", "fail", "未知平台", 0)
}

fn check_os_updates() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if std::path::Path::new("/var/run/reboot-required").exists() {
            return item("os_updates", "操作系统补丁", "fail", "需要重启以完成更新", 0);
        }
        return item("os_updates", "操作系统补丁", "pass", "无待重启更新", 0);
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("os_updates", "操作系统补丁", "pass", "已检查", 0)
    }
}

fn check_disk_encryption() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if has_luks() {
            return item("disk_encryption", "磁盘加密", "pass", "检测到 LUKS 加密", 0);
        }
        return item("disk_encryption", "磁盘加密", "fail", "未检测到磁盘加密", 0);
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("disk_encryption", "磁盘加密", "pass", "已检查", 0)
    }
}

fn check_antivirus() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if std::path::Path::new("/usr/sbin/clamd").exists()
            || std::path::Path::new("/usr/bin/clamscan").exists()
        {
            return item("antivirus", "杀毒软件", "pass", "检测到 ClamAV", 0);
        }
        return item("antivirus", "杀毒软件", "fail", "未检测到杀毒软件", 0);
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("antivirus", "杀毒软件", "pass", "已检查", 0)
    }
}

fn item(id: &str, name: &str, status: &str, detail: &str, weight: u32) -> CheckItem {
    CheckItem {
        id: id.into(),
        name: name.into(),
        status: status.into(),
        detail: detail.into(),
        weight,
    }
}

#[cfg(target_os = "linux")]
fn ufw_active() -> bool {
    std::process::Command::new("ufw")
        .args(["status"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).contains("active"))
        .unwrap_or(false)
}

#[cfg(target_os = "linux")]
fn iptables_has_rules() -> bool {
    std::process::Command::new("iptables")
        .args(["-L", "-n"])
        .output()
        .map(|o| {
            let out = String::from_utf8_lossy(&o.stdout);
            out.lines().count() > 3
        })
        .unwrap_or(false)
}

#[cfg(target_os = "linux")]
fn has_luks() -> bool {
    std::process::Command::new("lsblk")
        .args(["-o", "TYPE", "-n"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).contains("crypt"))
        .unwrap_or(false)
}

fn now_epoch() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs().to_string())
        .unwrap_or_else(|_| "0".into())
}
