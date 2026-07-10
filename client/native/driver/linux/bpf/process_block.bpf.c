// SPDX-License-Identifier: GPL-2.0
/* SentinelHub LSM BPF — block process execution by comm name.
 * Build: make -C client/native/driver/linux/bpf
 * Load:  sudo ./load.sh
 *
 * Requires kernel >= 5.7 with CONFIG_BPF_LSM=y.
 */

#include <linux/bpf.h>
#include <linux/lsm_hooks.h>
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_tracing.h>

#define MAX_BLOCK_NAMES 32
#define COMM_LEN 16

struct {
	__uint(type, BPF_MAP_TYPE_HASH);
	__uint(max_entries, MAX_BLOCK_NAMES);
	__type(key, char[COMM_LEN]);
	__type(value, __u32);
} blocked_comms SEC(".maps");

static __always_inline int comm_matches(const char *comm, const char *pattern)
{
#pragma unroll
	for (int i = 0; i < COMM_LEN - 1; i++) {
		if (pattern[i] == '\0')
			return 1;
		if (comm[i] != pattern[i])
			return 0;
	}
	return pattern[COMM_LEN - 1] == '\0';
}

SEC("lsm/bprm_check_security")
int sentinel_bprm_check(struct linux_binprm *bprm)
{
	char comm[COMM_LEN];
	__u32 *action;
	int i;

	bpf_probe_read_kernel_str(comm, sizeof(comm), bprm->filename);

	for (i = 0; i < MAX_BLOCK_NAMES; i++) {
		char key[COMM_LEN] = {};
		/* Userspace populates map keys; iterate via known entries */
		action = bpf_map_lookup_elem(&blocked_comms, &key);
		if (!action)
			continue;
		if (comm_matches(comm, key) && *action == 1)
			return -EPERM;
	}
	return 0;
}

char LICENSE[] SEC("license") = "GPL";
