//! Linux kernel module ioctl client (/dev/sentinelhub).

#[cfg(target_os = "linux")]
mod linux {
    use std::fs::OpenOptions;
    use std::io;
    use std::os::unix::io::AsRawFd;

    const SENTINEL_IOC_MAGIC: u8 = b'S';
    const SENTINEL_POLICY_MAX: usize = 4096;
    pub const SENTINEL_EVENT_FILE_OPEN: u32 = 1;
    pub const SENTINEL_EVENT_FILE_BLOCK: u32 = 2;
    pub const SENTINEL_EVENT_PROCESS_EXEC: u32 = 3;
    pub const SENTINEL_EVENT_PROCESS_BLOCK: u32 = 4;

    #[repr(C)]
    #[derive(Default, Clone)]
    pub struct SentinelStatus {
        pub version: u32,
        pub flags: u32,
        pub policy_len: u32,
        pub event_count: u32,
        pub mode: [u8; 32],
    }

    #[repr(C)]
    pub struct SentinelPolicyReq {
        pub len: u32,
        pub data: [u8; SENTINEL_POLICY_MAX],
    }

    #[repr(C)]
    #[derive(Clone)]
    pub struct SentinelEvent {
        pub type_: u32,
        pub pid: u32,
        pub blocked: u32,
        pub path: [u8; 256],
    }

    impl Default for SentinelEvent {
        fn default() -> Self {
            Self {
                type_: 0,
                pid: 0,
                blocked: 0,
                path: [0u8; 256],
            }
        }
    }

    pub fn probe() -> Option<SentinelStatus> {
        let file = open_dev().ok()?;
        let mut st = SentinelStatus::default();
        ioctl(
            file.as_raw_fd(),
            ioctl_ior(1, std::mem::size_of::<SentinelStatus>()),
            &mut st,
        )
        .ok()?;
        Some(st)
    }

    pub fn get_policy() -> io::Result<Vec<u8>> {
        let file = open_dev()?;
        let mut req = SentinelPolicyReq {
            len: 0,
            data: [0u8; SENTINEL_POLICY_MAX],
        };
        ioctl(
            file.as_raw_fd(),
            ioctl_ior(3, std::mem::size_of::<SentinelPolicyReq>()),
            &mut req,
        )?;
        Ok(req.data[..req.len as usize].to_vec())
    }

    pub fn set_policy(payload: &[u8]) -> io::Result<()> {
        if payload.len() > SENTINEL_POLICY_MAX {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "policy too large",
            ));
        }
        let file = open_dev()?;
        let mut req = SentinelPolicyReq {
            len: payload.len() as u32,
            data: [0u8; SENTINEL_POLICY_MAX],
        };
        req.data[..payload.len()].copy_from_slice(payload);
        ioctl(
            file.as_raw_fd(),
            ioctl_iow(2, std::mem::size_of::<SentinelPolicyReq>()),
            &mut req,
        )
    }

    pub fn push_event(ev: &SentinelEvent) -> io::Result<()> {
        let file = open_dev()?;
        let mut copy = ev.clone();
        ioctl(
            file.as_raw_fd(),
            ioctl_iow(4, std::mem::size_of::<SentinelEvent>()),
            &mut copy,
        )
    }

    pub fn get_event() -> io::Result<SentinelEvent> {
        let file = open_dev()?;
        let mut ev = SentinelEvent::default();
        ioctl(
            file.as_raw_fd(),
            ioctl_ior(5, std::mem::size_of::<SentinelEvent>()),
            &mut ev,
        )?;
        Ok(ev)
    }

    fn open_dev() -> io::Result<std::fs::File> {
        OpenOptions::new()
            .read(true)
            .write(true)
            .open("/dev/sentinelhub")
    }

    fn ioctl<T>(fd: i32, request: libc::c_ulong, arg: &mut T) -> io::Result<()> {
        let rc = unsafe { libc::ioctl(fd, request, arg as *mut T) };
        if rc < 0 {
            return Err(io::Error::last_os_error());
        }
        Ok(())
    }

    fn ioctl_iow(nr: u8, size: usize) -> libc::c_ulong {
        (SENTINEL_IOC_MAGIC as libc::c_ulong) << 8
            | (nr as libc::c_ulong)
            | ((size as libc::c_ulong) << 16)
    }

    fn ioctl_ior(nr: u8, size: usize) -> libc::c_ulong {
        ioctl_iow(nr, size)
    }

    pub fn path_to_bytes(path: &str) -> [u8; 256] {
        let mut buf = [0u8; 256];
        let bytes = path.as_bytes();
        let len = bytes.len().min(255);
        buf[..len].copy_from_slice(&bytes[..len]);
        buf
    }
}

#[cfg(target_os = "linux")]
pub use linux::{
    get_event, get_policy, path_to_bytes, probe, push_event, set_policy, SentinelEvent,
    SENTINEL_EVENT_FILE_BLOCK, SENTINEL_EVENT_FILE_OPEN, SENTINEL_EVENT_PROCESS_BLOCK,
    SENTINEL_EVENT_PROCESS_EXEC,
};

#[cfg(not(target_os = "linux"))]
pub fn probe() -> Option<()> {
    None
}

#[cfg(not(target_os = "linux"))]
pub fn set_policy(_payload: &[u8]) -> std::io::Result<()> {
    Err(std::io::Error::new(
        std::io::ErrorKind::Unsupported,
        "kernel module only on Linux",
    ))
}
