//! Kernel/driver IPC placeholder for P2+ deep enforcement.
//!
//! Future: communicate with `client/native/driver/` out-of-process module
//! for file-system filter, USB block at driver level, and network isolation.

use serde::Serialize;

#[derive(Serialize)]
pub struct DriverStatus {
    pub available: bool,
    pub mode: &'static str,
    pub message: &'static str,
}

pub fn status() -> DriverStatus {
    DriverStatus {
        available: false,
        mode: "stub",
        message: "driver module not loaded; using userspace enforcers",
    }
}
