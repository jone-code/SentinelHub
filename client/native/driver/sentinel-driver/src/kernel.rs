//! Linux kernel module ioctl client (/dev/sentinelhub).

#[cfg(target_os = "linux")]
mod linux {
    use std::fs::OpenOptions;
    use std::io;
    use std::os::unix::io::AsRawFd;

    const SENTINEL_IOC_MAGIC: u8 = b'S';
    const SENTINEL_POLICY_MAX: usize = 4096;

    #[repr(C)]
    #[derive(Default, Clone)]
    pub struct SentinelStatus {
        pub version: u32,
        pub flags: u32,
        pub policy_len: u32,
        pub mode: [u8; 32],
    }

    #[repr(C)]
    pub struct SentinelPolicyReq {
        pub len: u32,
        pub data: [u8; SENTINEL_POLICY_MAX],
    }

    pub fn probe() -> Option<SentinelStatus> {
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .open("/dev/sentinelhub")
            .ok()?;
        let mut st = SentinelStatus::default();
        ioctl(
            file.as_raw_fd(),
            ioctl_ior(1, std::mem::size_of::<SentinelStatus>()),
            &mut st,
        )
        .ok()?;
        Some(st)
    }

    pub fn set_policy(payload: &[u8]) -> io::Result<()> {
        if payload.len() > SENTINEL_POLICY_MAX {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "policy too large",
            ));
        }
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .open("/dev/sentinelhub")?;
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
}

#[cfg(target_os = "linux")]
pub use linux::{probe, set_policy, SentinelStatus};

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
