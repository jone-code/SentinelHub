//! Sync process_block policy to pinned BPF map via bpftool.

#[cfg(target_os = "linux")]
mod linux {
    use crate::policy_parse::{blocked_process_names, parse_process_rules};
    use std::path::Path;
    use std::process::Command;

    const PINNED_MAP: &str = "/sys/fs/bpf/sentinel_blocked_comms";

    pub fn sync_from_policy(policy_json: &str) -> Result<usize, String> {
        if !Path::new(PINNED_MAP).exists() {
            return Err("BPF map not pinned; run linux/bpf/load.sh first".into());
        }
        let rules = parse_process_rules(policy_json);
        let names = blocked_process_names(&rules);
        clear_map()?;
        let mut updated = 0usize;
        for name in names {
            if update_key(&name)? {
                updated += 1;
            }
        }
        Ok(updated)
    }

    fn clear_map() -> Result<(), String> {
        let output = Command::new("bpftool")
            .args(["map", "dump", "pinned", PINNED_MAP])
            .output()
            .map_err(|e| format!("bpftool dump failed: {e}"))?;
        if !output.status.success() {
            return Ok(());
        }
        let text = String::from_utf8_lossy(&output.stdout);
        for line in text.lines() {
            if let Some(key) = line.split(':').next() {
                let key = key.trim().trim_matches('"');
                if !key.is_empty() {
                    let _ = Command::new("bpftool")
                        .args(["map", "delete", "pinned", PINNED_MAP, "key", key])
                        .status();
                }
            }
        }
        Ok(())
    }

    fn update_key(name: &str) -> Result<bool, String> {
        use std::io::Write;
        use std::process::Stdio;

        let key = name.chars().take(15).collect::<String>();
        if key.is_empty() {
            return Ok(false);
        }
        let mut child = Command::new("bpftool")
            .args([
                "map",
                "update",
                "pinned",
                PINNED_MAP,
                "key",
                "-",
                "value",
                "1",
                "0",
                "0",
                "0",
            ])
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| format!("bpftool update failed: {e}"))?;
        if let Some(mut stdin) = child.stdin.take() {
            let _ = stdin.write_all(key.as_bytes());
        }
        let status = child.wait().map_err(|e| format!("bpftool wait failed: {e}"))?;
        Ok(status.success())
    }
}

#[cfg(target_os = "linux")]
pub use linux::sync_from_policy;

#[cfg(not(target_os = "linux"))]
pub fn sync_from_policy(_policy_json: &str) -> Result<usize, String> {
    Err("BPF sync requires Linux".into())
}
