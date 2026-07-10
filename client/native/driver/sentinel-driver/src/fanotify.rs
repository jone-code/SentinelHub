//! fanotify file-open hook (Linux phase 2). Requires CAP_SYS_ADMIN.

#[cfg(target_os = "linux")]
mod linux {
    use crate::kernel::{
        path_to_bytes, push_event, SentinelEvent, SENTINEL_EVENT_FILE_BLOCK, SENTINEL_EVENT_FILE_OPEN,
    };
    use serde::Serialize;
    use std::ffi::CString;
    use std::fs::read_link;
    use std::io;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::{Arc, Mutex};
    use std::thread::{self, JoinHandle};

    const FAN_CLASS_CONTENT: u32 = 0x0000_0004;
    const FAN_CLOEXEC: u32 = 0x0000_0001;
    const FAN_MARK_ADD: u32 = 0x0000_0001;
    const FAN_MARK_MOUNT: u32 = 0x0000_0010;
    const FAN_OPEN_PERM: u64 = 0x0004_0000;
    const FAN_ACCESS_PERM: u64 = 0x0002_0000;
    const FAN_EVENT_ON_CHILD: u64 = 0x0000_0080;
    const FAN_ALLOW: u32 = 0x0000_0001;
    const FAN_DENY: u32 = 0x0000_0002;

    #[repr(C)]
    #[derive(Copy, Clone)]
    struct FanotifyEventMetadata {
        event_len: u32,
        vers: u8,
        reserved: u8,
        metadata_len: u16,
        mask: u64,
        fd: i32,
        pid: i32,
    }

    #[repr(C)]
    struct FanotifyResponse {
        fd: i32,
        response: u32,
    }

    #[derive(Clone)]
    struct FileRule {
        pattern: String,
        action: String,
    }

    #[derive(Clone, Serialize)]
    pub struct FileEvent {
        pub path: String,
        pub pid: u32,
        pub blocked: bool,
        pub action: String,
    }

    pub struct FanotifyWatcher {
        stop: Arc<AtomicBool>,
        handle: Option<JoinHandle<()>>,
        events: Arc<Mutex<Vec<FileEvent>>>,
    }

    impl FanotifyWatcher {
        pub fn start(policy_json: &str) -> io::Result<Self> {
            let rules = parse_file_rules(policy_json);
            if rules.is_empty() {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "no file_hook rules in policy",
                ));
            }

            let fan_fd = unsafe {
                libc::fanotify_init(
                    (FAN_CLASS_CONTENT | FAN_CLOEXEC) as libc::c_uint,
                    (libc::O_RDONLY | libc::O_LARGEFILE) as libc::c_uint,
                )
            };
            if fan_fd < 0 {
                return Err(io::Error::last_os_error());
            }

            let home = std::env::var("HOME").unwrap_or_else(|_| "/".into());
            let watch_path = CString::new(home)
                .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "invalid HOME"))?;
            let mask = FAN_OPEN_PERM | FAN_ACCESS_PERM | FAN_EVENT_ON_CHILD;
            let rc = unsafe {
                libc::fanotify_mark(
                    fan_fd,
                    FAN_MARK_ADD | FAN_MARK_MOUNT,
                    mask,
                    libc::AT_FDCWD,
                    watch_path.as_ptr(),
                )
            };
            if rc < 0 {
                unsafe { libc::close(fan_fd) };
                return Err(io::Error::last_os_error());
            }

            let stop = Arc::new(AtomicBool::new(false));
            let events = Arc::new(Mutex::new(Vec::new()));
            let stop_thread = stop.clone();
            let events_thread = events.clone();
            let handle = thread::spawn(move || {
                fanotify_loop(fan_fd, rules, stop_thread, events_thread);
            });

            Ok(Self {
                stop,
                handle: Some(handle),
                events,
            })
        }

        pub fn recent_events(&self, limit: usize) -> Vec<FileEvent> {
            let guard = self.events.lock().unwrap();
            let start = guard.len().saturating_sub(limit);
            guard[start..].to_vec()
        }
    }

    impl Drop for FanotifyWatcher {
        fn drop(&mut self) {
            self.stop.store(true, Ordering::SeqCst);
            if let Some(handle) = self.handle.take() {
                let _ = handle.join();
            }
        }
    }

    fn fanotify_loop(
        fan_fd: i32,
        rules: Vec<FileRule>,
        stop: Arc<AtomicBool>,
        events: Arc<Mutex<Vec<FileEvent>>>,
    ) {
        let mut buf = [0u8; 4096];
        while !stop.load(Ordering::SeqCst) {
            let n = unsafe {
                libc::read(
                    fan_fd,
                    buf.as_mut_ptr() as *mut libc::c_void,
                    buf.len(),
                )
            };
            if n <= 0 {
                thread::sleep(std::time::Duration::from_millis(50));
                continue;
            }
            let mut offset = 0usize;
            while offset + std::mem::size_of::<FanotifyEventMetadata>() <= n as usize {
                let meta = unsafe {
                    std::ptr::read_unaligned(
                        buf.as_ptr().add(offset) as *const FanotifyEventMetadata,
                    )
                };
                if meta.event_len == 0 {
                    break;
                }
                if meta.fd >= 0 {
                    process_event(fan_fd, meta, &rules, &events);
                }
                offset += meta.event_len as usize;
            }
        }
        unsafe { libc::close(fan_fd) };
    }

    fn process_event(
        fan_fd: i32,
        meta: FanotifyEventMetadata,
        rules: &[FileRule],
        events: &Arc<Mutex<Vec<FileEvent>>>,
    ) {
        let path = fd_to_path(meta.fd);
        let (blocked, action) = match rules.iter().find(|r| path_matches(&path, &r.pattern)) {
            Some(rule) if rule.action == "block" => (true, "block".to_string()),
            Some(rule) => (false, rule.action.clone()),
            None => {
                respond_fd(fan_fd, meta.fd, FAN_ALLOW);
                unsafe { libc::close(meta.fd) };
                return;
            }
        };

        let event_type = if blocked {
            SENTINEL_EVENT_FILE_BLOCK
        } else {
            SENTINEL_EVENT_FILE_OPEN
        };
        let _ = push_event(&SentinelEvent {
            type_: event_type,
            pid: meta.pid as u32,
            blocked: blocked as u32,
            path: path_to_bytes(&path),
        });

        let file_event = FileEvent {
            path: path.clone(),
            pid: meta.pid as u32,
            blocked,
            action,
        };
        {
            let mut guard = events.lock().unwrap();
            guard.push(file_event);
            if guard.len() > 200 {
                let drain = guard.len() - 200;
                guard.drain(0..drain);
            }
        }

        respond_fd(
            fan_fd,
            meta.fd,
            if blocked { FAN_DENY } else { FAN_ALLOW },
        );
        unsafe { libc::close(meta.fd) };
    }

    fn respond_fd(fan_fd: i32, fd: i32, response: u32) {
        let resp = FanotifyResponse { fd, response };
        unsafe {
            libc::write(
                fan_fd,
                &resp as *const _ as *const libc::c_void,
                std::mem::size_of::<FanotifyResponse>(),
            );
        }
    }

    fn fd_to_path(fd: i32) -> String {
        let link = format!("/proc/self/fd/{fd}");
        read_link(&link)
            .map(|p| p.to_string_lossy().into_owned())
            .unwrap_or_else(|_| format!("fd:{fd}"))
    }

    fn parse_file_rules(policy_json: &str) -> Vec<FileRule> {
        let value: serde_json::Value = match serde_json::from_str(policy_json) {
            Ok(v) => v,
            Err(_) => return vec![],
        };
        let items = if let Some(arr) = value.as_array() {
            arr.clone()
        } else if let Some(rules) = value.get("rules").and_then(|r| r.as_array()) {
            rules.clone()
        } else {
            return vec![];
        };

        let mut rules = Vec::new();
        for item in items {
            let channel = item.get("channel").and_then(|c| c.as_str()).unwrap_or("");
            if channel != "sensitive_path" && channel != "file_hook" {
                continue;
            }
            let action = item
                .get("action")
                .and_then(|a| a.as_str())
                .unwrap_or("alert")
                .to_string();
            if let Some(patterns) = item.get("patterns").and_then(|p| p.as_array()) {
                for p in patterns {
                    if let Some(s) = p.as_str() {
                        rules.push(FileRule {
                            pattern: s.to_string(),
                            action: action.clone(),
                        });
                    }
                }
            }
        }
        rules
    }

    fn path_matches(path: &str, pattern: &str) -> bool {
        let p = pattern.trim();
        if p.starts_with('*') {
            return path.ends_with(p.trim_start_matches('*'));
        }
        path.contains(p)
    }
}

#[cfg(target_os = "linux")]
pub use linux::FanotifyWatcher;
