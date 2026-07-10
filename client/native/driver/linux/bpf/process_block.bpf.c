// SPDX-License-Identifier: GPL-2.0
/* SentinelHub LSM BPF — block process execution by comm name. */

#include <linux/bpf.h>
#include <linux/lsm_hooks.h>
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_tracing.h>

#define COMM_LEN 16

struct {
	__uint(type, BPF_MAP_TYPE_HASH);
	__uint(max_entries, 32);
	__type(key, char[COMM_LEN]);
	__type(value, __u32);
} blocked_comms SEC(".maps");

static __always_inline void basename_to_key(const char *path, char *key)
{
	int i = 0;
	int last_slash = -1;

	while (path[i] != '\0' && i < 255) {
		if (path[i] == '/')
			last_slash = i;
		i++;
	}

#pragma unroll
	for (i = 0; i < COMM_LEN - 1; i++) {
		char c = path[last_slash + 1 + i];
		if (c == '\0')
			break;
		key[i] = c;
	}
}

SEC("lsm/bprm_check_security")
int sentinel_bprm_check(struct linux_binprm *bprm)
{
	char fname[256] = {};
	char key[COMM_LEN] = {};
	__u32 *action;

	bpf_probe_read_kernel_str(fname, sizeof(fname), (void *)bprm->filename);
	basename_to_key(fname, key);
	action = bpf_map_lookup_elem(&blocked_comms, &key);
	if (action && *action == 1)
		return -EPERM;
	return 0;
}

char LICENSE[] SEC("license") = "GPL";
