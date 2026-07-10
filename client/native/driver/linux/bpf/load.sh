#!/bin/bash
# Load SentinelHub process_block BPF LSM program (requires root + bpftool).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OBJ="$DIR/process_block.bpf.o"

if [[ ! -f "$OBJ" ]]; then
  echo "Building BPF object..."
  make -C "$DIR"
fi

if ! command -v bpftool &>/dev/null; then
  echo "bpftool not found; install linux-tools-common or use userspace process_block watcher"
  exit 1
fi

echo "Loading $OBJ via bpftool..."
bpftool prog load "$OBJ" /sys/fs/bpf/sentinel_process_block type lsm
bpftool prog attach id "$(bpftool prog show pinned /sys/fs/bpf/sentinel_process_block -j | jq -r '.[0].id')" lsm
echo "BPF LSM process_block attached. Populate blocked_comms map via bpftool map update."
