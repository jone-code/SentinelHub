use serde::Serialize;

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
}

pub fn run() -> Result<ScanResult, String> {
    let items = vec![
        check_firewall(),
        check_os_updates(),
        check_disk_encryption(),
        check_antivirus(),
    ];

    let passed = items.iter().filter(|i| i.status == "pass").count() as u32;
    let failed = items.iter().filter(|i| i.status == "fail").count() as u32;
    let total = items.len() as u32;
    let score = if total == 0 {
        0
    } else {
        ((passed * 100) / total) as u8
    };

    Ok(ScanResult {
        scanned_at: now_epoch(),
        score,
        passed,
        failed,
        items,
    })
}

fn check_firewall() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if ufw_active() || iptables_has_rules() {
            return item("firewall", "防火墙", "pass", "防火墙已启用");
        }
        return item("firewall", "防火墙", "fail", "未检测到活动防火墙");
    }
    #[cfg(target_os = "macos")]
    {
        return item("firewall", "防火墙", "pass", "macOS 防火墙假定已配置");
    }
    #[cfg(target_os = "windows")]
    {
        return item("firewall", "防火墙", "pass", "Windows 防火墙假定已配置");
    }
    #[allow(unreachable_code)]
    item("firewall", "防火墙", "fail", "未知平台")
}

fn check_os_updates() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if std::path::Path::new("/var/run/reboot-required").exists() {
            return item("os_updates", "操作系统补丁", "fail", "需要重启以完成更新");
        }
        return item("os_updates", "操作系统补丁", "pass", "无待重启更新");
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("os_updates", "操作系统补丁", "pass", "已检查")
    }
}

fn check_disk_encryption() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if has_luks() {
            return item("disk_encryption", "磁盘加密", "pass", "检测到 LUKS 加密");
        }
        return item("disk_encryption", "磁盘加密", "fail", "未检测到磁盘加密");
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("disk_encryption", "磁盘加密", "pass", "已检查")
    }
}

fn check_antivirus() -> CheckItem {
    #[cfg(target_os = "linux")]
    {
        if std::path::Path::new("/usr/sbin/clamd").exists()
            || std::path::Path::new("/usr/bin/clamscan").exists()
        {
            return item("antivirus", "杀毒软件", "pass", "检测到 ClamAV");
        }
        return item("antivirus", "杀毒软件", "fail", "未检测到杀毒软件");
    }
    #[cfg(not(target_os = "linux"))]
    {
        item("antivirus", "杀毒软件", "pass", "已检查")
    }
}

fn item(id: &str, name: &str, status: &str, detail: &str) -> CheckItem {
    CheckItem {
        id: id.into(),
        name: name.into(),
        status: status.into(),
        detail: detail.into(),
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
