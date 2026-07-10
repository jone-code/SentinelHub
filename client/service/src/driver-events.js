/**
 * Normalize kernel/driver daemon events for backend audit ingest.
 */

const KERNEL_EVENT_TYPES = {
  1: 'driver.file_open',
  2: 'driver.file_blocked',
  3: 'driver.process_exec',
  4: 'driver.process_blocked',
};

/**
 * @param {object | null | undefined} batch
 * @returns {Array<{ event_type: string, severity: string, detail: object }>}
 */
export function normalizeDriverEvents(batch) {
  if (!batch) return [];

  const events = [];
  for (const ev of batch.file_events ?? []) {
    events.push(normalizeFileEvent(ev, 'daemon'));
  }
  for (const ev of batch.process_events ?? []) {
    events.push(normalizeProcessEvent(ev, 'daemon'));
  }
  for (const ev of batch.kernel_events ?? []) {
    events.push(normalizeKernelEvent(ev));
  }
  return events;
}

/**
 * @param {object} ev
 * @param {'daemon' | 'kernel'} source
 */
function normalizeFileEvent(ev, source) {
  const blocked = Boolean(ev.blocked);
  return {
    event_type: blocked ? 'driver.file_blocked' : 'driver.file_open',
    severity: blocked ? 'critical' : 'info',
    detail: {
      source,
      path: ev.path,
      pid: ev.pid,
      blocked,
      action: ev.action ?? (blocked ? 'block' : 'alert'),
    },
  };
}

/**
 * @param {object} ev
 * @param {'daemon' | 'kernel'} source
 */
function normalizeProcessEvent(ev, source) {
  const blocked = Boolean(ev.blocked);
  return {
    event_type: blocked ? 'driver.process_blocked' : 'driver.process_exec',
    severity: blocked ? 'critical' : 'info',
    detail: {
      source,
      process: ev.process,
      pid: ev.pid,
      blocked,
      action: ev.action ?? (blocked ? 'block' : 'alert'),
    },
  };
}

/**
 * @param {object} ev
 */
function normalizeKernelEvent(ev) {
  const type = Number(ev.type);
  const blocked = Boolean(ev.blocked);
  const eventType = KERNEL_EVENT_TYPES[type] ?? 'driver.unknown';
  const isFile = type === 1 || type === 2;
  const detail = {
    source: 'kernel',
    kernel_type: type,
    pid: ev.pid,
    blocked,
  };
  if (isFile) {
    detail.path = ev.path;
  } else {
    detail.process = ev.path;
  }
  return {
    event_type: eventType,
    severity: blocked ? 'critical' : 'info',
    detail,
  };
}

/**
 * Stable dedup key for driver event stream.
 * @param {{ event_type: string, detail: object }} event
 */
export function driverEventDedupKey(event) {
  const d = event.detail ?? {};
  const subject = d.path ?? d.process ?? 'unknown';
  return `${event.event_type}:${d.pid ?? 0}:${subject}`;
}
