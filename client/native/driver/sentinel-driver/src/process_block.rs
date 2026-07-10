//! Process execution watcher (Linux phase 3).
//! Monitors /proc for new processes and blocks matches from policy rules.
//! Complements optional LSM BPF program in linux/bpf/.

#[cfg(target_os = "linux")]
mod linux {
    use crate::kernel::{
        path_to_bytes, push_event, SentinelEvent, SENTINEL_EVENT_PROCESS_BLOCK,
        SENTINEL_EVENT_PROCESS_EXEC,
    };
    use crate::policy_parse::{parse_process_rules, ProcessRule};
    use serde::Serialize;
    use std::collections::HashSet;
    use std::fs;
    use std::io;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::{Arc, Mutex};
    use std::thread::{self, JoinHandle};
    use std::time::Duration;

    #[derive(Clone, Serialize)]
    pub struct ProcessEvent {
        pub process: String,
        pub pid: u32,
        pub blocked: bool,
        pub action: String,
    }

    pub struct ProcessWatcher {
        stop: Arc<AtomicBool>,
        handle: Option<JoinHandle<()>>,
        events: Arc<Mutex<Vec<ProcessEvent>>>,
    }

    impl ProcessWatcher {
        pub fn start(policy_json: &str) -> io::Result<Self> {
            let rules = parse_process_rules(policy_json);
            if rules.is_empty() {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "no process_block rules in policy",
                ));
            }

            let stop = Arc::new(AtomicBool::new(false));
            let events = Arc::new(Mutex::new(Vec::new()));
            let known = Arc::new(Mutex::new(snapshot_pids()));
            let stop_thread = stop.clone();
            let events_thread = events.clone();
            let handle = thread::spawn(move || {
                process_loop(rules, stop_thread, events_thread, known);
            });

            Ok(Self {
                stop,
                handle: Some(handle),
                events,
            })
        }

        pub fn recent_events(&self, limit: usize) -> Vec<ProcessEvent> {
            let guard = self.events.lock().unwrap();
            let start = guard.len().saturating_sub(limit);
            guard[start..].to_vec()
        }
    }

    impl Drop for ProcessWatcher {
        fn drop(&mut self) {
            self.stop.store(true, Ordering::SeqCst);
            if let Some(handle) = self.handle.take() {
                let _ = handle.join();
            }
        }
    }

    fn process_loop(
        rules: Vec<ProcessRule>,
        stop: Arc<AtomicBool>,
        events: Arc<Mutex<Vec<ProcessEvent>>>,
        known: Arc<Mutex<HashSet<u32>>>,
    ) {
        while !stop.load(Ordering::SeqCst) {
            let current = snapshot_pids();
            let mut guard = known.lock().unwrap();
            for pid in current.difference(&*guard) {
                if let Some((name, blocked, action)) = check_pid(*pid, &rules) {
                    record_event(*pid, &name, blocked, &action, &events);
                }
            }
            *guard = current;
            drop(guard);
            thread::sleep(Duration::from_millis(500));
        }
    }

    fn check_pid(pid: u32, rules: &[ProcessRule]) -> Option<(String, bool, String)> {
        if pid <= 1 {
            return None;
        }
        let comm_path = format!("/proc/{pid}/comm");
        let name = fs::read_to_string(&comm_path).ok()?;
        let norm = normalize_name(&name);
        let rule = rules.iter().find(|r| normalize_name(&r.name) == norm)?;
        let blocked = if rule.action == "block" {
            terminate_pid(pid)
        } else {
            false
        };
        Some((name.trim().to_string(), blocked, rule.action.clone()))
    }

    fn record_event(
        pid: u32,
        name: &str,
        blocked: bool,
        action: &str,
        events: &Arc<Mutex<Vec<ProcessEvent>>>,
    ) {
        let event_type = if blocked {
            SENTINEL_EVENT_PROCESS_BLOCK
        } else {
            SENTINEL_EVENT_PROCESS_EXEC
        };
        let _ = push_event(&SentinelEvent {
            type_: event_type,
            pid,
            blocked: blocked as u32,
            path: path_to_bytes(name),
        });

        let ev = ProcessEvent {
            process: name.to_string(),
            pid,
            blocked,
            action: action.to_string(),
        };
        let mut guard = events.lock().unwrap();
        guard.push(ev);
        if guard.len() > 200 {
            let drain = guard.len() - 200;
            guard.drain(0..drain);
        }
    }

    fn snapshot_pids() -> HashSet<u32> {
        let mut pids = HashSet::new();
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.chars().all(|c| c.is_ascii_digit()) {
                    if let Ok(pid) = name.parse::<u32>() {
                        pids.insert(pid);
                    }
                }
            }
        }
        pids
    }

    fn terminate_pid(pid: u32) -> bool {
        if pid <= 1 {
            return false;
        }
        unsafe { libc::kill(pid as i32, libc::SIGKILL) == 0 }
    }

    fn normalize_name(name: &str) -> String {
        name.trim()
            .to_lowercase()
            .trim_end_matches(".exe")
            .to_string()
    }
}

#[cfg(target_os = "linux")]
pub use linux::ProcessWatcher;
