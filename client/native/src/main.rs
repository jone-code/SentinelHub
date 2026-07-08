//! SentinelHub native sidecar
//!
//!   sentinel-native collect --json
//!   sentinel-native enforce software --policy-file <path> --json

mod enforce;
mod scan;

use serde::Serialize;
use std::env;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize)]
struct AssetSnapshot {
    source: &'static str,
    collected_at: String,
    hardware: HardwareInfo,
    software: Vec<SoftwareItem>,
}

#[derive(Serialize)]
struct HardwareInfo {
    hostname: String,
    os_type: String,
    os_version: String,
    arch: String,
    cpu_cores: usize,
    memory_total_mb: u64,
}

#[derive(Serialize)]
struct SoftwareItem {
    name: String,
    version: String,
}

fn main() {
    let args: Vec<String> = env::args().collect();

    if args.len() >= 3 && args[1] == "collect" && args[2] == "--json" {
        match collect_assets() {
            Ok(json) => println!("{json}"),
            Err(err) => {
                eprintln!("{err}");
                std::process::exit(1);
            }
        }
        return;
    }

    if args.len() >= 4 && args[1] == "enforce" && args[2] == "software" {
        let policy_path = parse_flag_path(&args, "--policy-file")
            .unwrap_or_else(|| {
                eprintln!("--policy-file required");
                std::process::exit(2);
            });
        let json_flag = args.iter().any(|a| a == "--json");
        match enforce::software::run(&policy_path) {
            Ok(result) => {
                if json_flag {
                    println!("{}", serde_json::to_string(&result).unwrap_or_else(|_| "{}".into()));
                }
            }
            Err(err) => {
                eprintln!("{err}");
                std::process::exit(1);
            }
        }
        return;
    }

    if args.len() >= 3 && args[1] == "scan" && args[2] == "compliance" {
        let json_flag = args.iter().any(|a| a == "--json");
        match scan::compliance::run() {
            Ok(result) => {
                if json_flag {
                    println!("{}", serde_json::to_string(&result).unwrap_or_else(|_| "{}".into()));
                }
            }
            Err(err) => {
                eprintln!("{err}");
                std::process::exit(1);
            }
        }
        return;
    }

    eprintln!("usage:");
    eprintln!("  sentinel-native collect --json");
    eprintln!("  sentinel-native enforce software --policy-file <path> --json");
    eprintln!("  sentinel-native scan compliance --json");
    std::process::exit(2);
}

fn parse_flag_path(args: &[String], flag: &str) -> Option<PathBuf> {
    args.iter()
        .position(|a| a == flag)
        .and_then(|i| args.get(i + 1))
        .map(PathBuf::from)
}

fn collect_assets() -> Result<String, String> {
    let snapshot = AssetSnapshot {
        source: "native",
        collected_at: unix_now_iso(),
        hardware: HardwareInfo {
            hostname: read_hostname(),
            os_type: os_type().into(),
            os_version: read_os_version(),
            arch: env::consts::ARCH.into(),
            cpu_cores: std::thread::available_parallelism()
                .map(|n| n.get())
                .unwrap_or(1),
            memory_total_mb: 0,
        },
        software: vec![],
    };
    serde_json::to_string(&snapshot).map_err(|e| e.to_string())
}

fn read_hostname() -> String {
    env::var("HOSTNAME")
        .or_else(|_| env::var("COMPUTERNAME"))
        .unwrap_or_else(|_| "unknown".into())
}

fn os_type() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else if cfg!(target_os = "linux") {
        "linux"
    } else {
        "unknown"
    }
}

fn read_os_version() -> String {
    if cfg!(target_os = "linux") {
        std::fs::read_to_string("/etc/os-release")
            .ok()
            .and_then(|s| {
                s.lines()
                    .find(|l| l.starts_with("PRETTY_NAME="))
                    .map(|l| l.trim_start_matches("PRETTY_NAME=").trim_matches('"').to_string())
            })
            .unwrap_or_else(|| "linux".into())
    } else if cfg!(target_os = "macos") {
        "macOS".into()
    } else if cfg!(target_os = "windows") {
        "Windows".into()
    } else {
        "unknown".into()
    }
}

fn unix_now_iso() -> String {
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("{secs}")
}
