// SPDX-License-Identifier: GPL-2.0
/*
 * SentinelHub Linux kernel module — policy channel + event ring buffer.
 * Phase 2: userspace fanotify pushes file events via SENTINEL_IOC_PUSH_EVENT.
 */

#include <linux/module.h>
#include <linux/miscdevice.h>
#include <linux/fs.h>
#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/mutex.h>

#include "../../include/sentinel_ioctl.h"

#define DRIVER_VERSION 2

static DEFINE_MUTEX(policy_lock);
static DEFINE_MUTEX(event_lock);
static char *policy_buf;
static size_t policy_len;

static struct sentinel_event event_ring[SENTINEL_EVENT_RING_SIZE];
static unsigned int event_head;
static unsigned int event_tail;
static unsigned int event_count;

static void event_ring_push(const struct sentinel_event *ev)
{
	event_ring[event_head] = *ev;
	event_head = (event_head + 1) % SENTINEL_EVENT_RING_SIZE;
	if (event_count < SENTINEL_EVENT_RING_SIZE) {
		event_count++;
	} else {
		event_tail = (event_tail + 1) % SENTINEL_EVENT_RING_SIZE;
	}
}

static bool event_ring_pop(struct sentinel_event *ev)
{
	if (event_count == 0)
		return false;
	*ev = event_ring[event_tail];
	event_tail = (event_tail + 1) % SENTINEL_EVENT_RING_SIZE;
	event_count--;
	return true;
}

static long sentinel_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
	struct sentinel_status st;
	struct sentinel_policy_req req;
	struct sentinel_event ev;

	switch (cmd) {
	case SENTINEL_IOC_STATUS:
		memset(&st, 0, sizeof(st));
		st.version = DRIVER_VERSION;
		st.flags = SENTINEL_FLAG_KERNEL_LOADED;
		mutex_lock(&policy_lock);
		if (policy_len > 0)
			st.flags |= SENTINEL_FLAG_POLICY_SET;
		mutex_unlock(&policy_lock);
		mutex_lock(&event_lock);
		st.policy_len = policy_len;
		st.event_count = event_count;
		mutex_unlock(&event_lock);
		strscpy(st.mode, "kernel_lsm", sizeof(st.mode));
		if (copy_to_user((void __user *)arg, &st, sizeof(st)))
			return -EFAULT;
		return 0;

	case SENTINEL_IOC_SET_POLICY:
		if (copy_from_user(&req, (void __user *)arg, sizeof(req)))
			return -EFAULT;
		if (req.len > SENTINEL_POLICY_MAX)
			return -EINVAL;
		mutex_lock(&policy_lock);
		kfree(policy_buf);
		policy_buf = kmemdup(req.data, req.len, GFP_KERNEL);
		if (!policy_buf && req.len > 0) {
			mutex_unlock(&policy_lock);
			return -ENOMEM;
		}
		policy_len = req.len;
		mutex_unlock(&policy_lock);
		pr_info("sentinel_kmod: policy updated (%zu bytes)\n", policy_len);
		return 0;

	case SENTINEL_IOC_GET_POLICY:
		mutex_lock(&policy_lock);
		memset(&req, 0, sizeof(req));
		req.len = policy_len;
		if (policy_len > 0 && policy_buf)
			memcpy(req.data, policy_buf, policy_len);
		mutex_unlock(&policy_lock);
		if (copy_to_user((void __user *)arg, &req, sizeof(req)))
			return -EFAULT;
		return 0;

	case SENTINEL_IOC_PUSH_EVENT:
		if (copy_from_user(&ev, (void __user *)arg, sizeof(ev)))
			return -EFAULT;
		mutex_lock(&event_lock);
		event_ring_push(&ev);
		mutex_unlock(&event_lock);
		return 0;

	case SENTINEL_IOC_GET_EVENT:
		mutex_lock(&event_lock);
		if (!event_ring_pop(&ev)) {
			mutex_unlock(&event_lock);
			return -ENOENT;
		}
		mutex_unlock(&event_lock);
		if (copy_to_user((void __user *)arg, &ev, sizeof(ev)))
			return -EFAULT;
		return 0;

	default:
		return -ENOTTY;
	}
}

static int sentinel_open(struct inode *inode, struct file *file)
{
	return 0;
}

static int sentinel_release(struct inode *inode, struct file *file)
{
	return 0;
}

static const struct file_operations sentinel_fops = {
	.owner          = THIS_MODULE,
	.open           = sentinel_open,
	.release        = sentinel_release,
	.unlocked_ioctl = sentinel_ioctl,
#ifdef CONFIG_COMPAT
	.compat_ioctl   = sentinel_ioctl,
#endif
};

static struct miscdevice sentinel_misc = {
	.minor = MISC_DYNAMIC_MINOR,
	.name  = SENTINEL_DEVICE_NAME,
	.fops  = &sentinel_fops,
};

static int __init sentinel_init(void)
{
	int ret = misc_register(&sentinel_misc);
	if (ret)
		return ret;
	pr_info("sentinel_kmod: loaded v%d (/dev/%s)\n", DRIVER_VERSION, SENTINEL_DEVICE_NAME);
	return 0;
}

static void __exit sentinel_exit(void)
{
	mutex_lock(&policy_lock);
	kfree(policy_buf);
	policy_buf = NULL;
	policy_len = 0;
	mutex_unlock(&policy_lock);
	mutex_lock(&event_lock);
	event_head = event_tail = event_count = 0;
	mutex_unlock(&event_lock);
	misc_deregister(&sentinel_misc);
	pr_info("sentinel_kmod: unloaded\n");
}

module_init(sentinel_init);
module_exit(sentinel_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("SentinelHub");
MODULE_DESCRIPTION("SentinelHub enforcement policy + event ring (phase 2)");
MODULE_VERSION("0.2.0");
