#!/bin/bash
# Load SentinelHub process_block BPF LSM program (requires root + bpftool).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OBJ="$DIR/process_block.bpf.o"
MAP_PIN="/sys/fs/bpf/sentinel_blocked_comms"
PROG_PIN="/sys/fs/bpf/sentinel_process_block"

if [[ ! -f "$OBJ" ]]; then
  echo "Building BPF object..."
  make -C "$DIR"
fi

if ! command -v bpftool &>/dev/null; then
  echo "bpftool not found; install linux-tools-common"
  exit 1
fi

echo "Loading $OBJ via bpftool..."
bpftool prog load "$OBJ" "$PROG_PIN" type lsm
PROG_ID=$(bpftool prog show pinned "$PROG_PIN" -j | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")
MAP_ID=$(bpftool prog show pinned "$PROG_PIN" -j | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['map_ids'][0])")
bpftool map pin id "$MAP_ID" "$MAP_PIN"
bpftool prog attach id "$PROG_ID" lsm
echo "BPF LSM attached. Map pinned at $MAP_PIN"
