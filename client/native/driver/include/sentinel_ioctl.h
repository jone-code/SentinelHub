/* SentinelHub driver — shared ioctl definitions (Linux userspace + kernel). */
#ifndef SENTINEL_IOCTL_H
#define SENTINEL_IOCTL_H

#ifdef __linux__
#include <linux/ioctl.h>
#include <linux/types.h>
#else
#include <stdint.h>
#ifndef __user
#define __user
#endif
typedef uint32_t __u32;
typedef uint64_t __u64;
#define _IO(type, nr)        ((type) << 8 | (nr))
#define _IOR(type, nr, size) (((type) << 8) | (nr) | ((sizeof(size)) << 16))
#define _IOW(type, nr, size) (((type) << 8) | (nr) | ((sizeof(size)) << 16))
#endif

#define SENTINEL_DEVICE_NAME "sentinelhub"
#define SENTINEL_IOC_MAGIC   'S'

#define SENTINEL_IOC_STATUS      _IOR(SENTINEL_IOC_MAGIC, 1, struct sentinel_status)
#define SENTINEL_IOC_SET_POLICY  _IOW(SENTINEL_IOC_MAGIC, 2, struct sentinel_policy_req)
#define SENTINEL_IOC_GET_POLICY  _IOR(SENTINEL_IOC_MAGIC, 3, struct sentinel_policy_req)
#define SENTINEL_IOC_PUSH_EVENT  _IOW(SENTINEL_IOC_MAGIC, 4, struct sentinel_event)
#define SENTINEL_IOC_GET_EVENT   _IOR(SENTINEL_IOC_MAGIC, 5, struct sentinel_event)

#define SENTINEL_POLICY_MAX 4096
#define SENTINEL_EVENT_PATH_MAX 256
#define SENTINEL_EVENT_RING_SIZE 64

#define SENTINEL_EVENT_FILE_OPEN  1
#define SENTINEL_EVENT_FILE_BLOCK 2
#define SENTINEL_EVENT_PROCESS_EXEC  3
#define SENTINEL_EVENT_PROCESS_BLOCK 4

struct sentinel_status {
	__u32 version;
	__u32 flags;
	__u32 policy_len;
	__u32 event_count;
	char  mode[32];
};

struct sentinel_policy_req {
	__u32 len;
	char  data[SENTINEL_POLICY_MAX];
};

struct sentinel_event {
	__u32 type;
	__u32 pid;
	__u32 blocked;
	char  path[SENTINEL_EVENT_PATH_MAX];
};

/* flags */
#define SENTINEL_FLAG_KERNEL_LOADED 0x1
#define SENTINEL_FLAG_POLICY_SET    0x2
#define SENTINEL_FLAG_BPF_ATTACHED  0x4

#endif /* SENTINEL_IOCTL_H */
