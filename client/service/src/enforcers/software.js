import { writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runNative } from '../native-bridge.js';

/**
 * Extract software blacklist from cached policy bundle.
 * @param {object | null} policy
 */
export function extractSoftwareBlacklist(policy) {
  const config = policy?.rules?.software?.config ?? policy?.software?.config;
  if (!config) return { blacklist: [], action: 'alert' };
  return {
    blacklist: (config.blacklist ?? []).map((s) => String(s).toLowerCase()),
    action: config.action ?? 'alert',
  };
}

/**
 * Run software enforcement via native sidecar or Node fallback.
 * @param {string | null} nativeBin
 * @param {object | null} policy
 */
export async function enforceSoftware(nativeBin, policy) {
  const { blacklist, action } = extractSoftwareBlacklist(policy);
  if (blacklist.length === 0) {
    return { checked_at: new Date().toISOString(), violations: [] };
  }

  if (nativeBin) {
    const tmp = path.join(tmpdir(), `sentinel-policy-${process.pid}.json`);
    try {
      await writeFile(tmp, JSON.stringify(policy));
      const result = await runNative(nativeBin, ['enforce', 'software', '--policy-file', tmp, '--json']);
      return result;
    } catch (err) {
      console.warn('[sentinel-service] native enforce failed, fallback to node:', err.message);
      return enforceSoftwareNode(blacklist, action);
    } finally {
      await unlink(tmp).catch(() => {});
    }
  }

  return enforceSoftwareNode(blacklist, action);
}

/**
 * Node fallback — Linux /proc scan with optional process termination.
 */
async function enforceSoftwareNode(blacklist, action) {
  const processes = await listProcessesNode();
  /** @type {Map<string, {process: string, matched_rule: string, action: string, blocked: boolean, terminated_pids: number[]}>} */
  const violations = new Map();

  for (const proc of processes) {
    const matched = matchBlacklist(proc.name, blacklist);
    if (!matched) continue;

    const key = normalize(proc.name);
    const entry = violations.get(key) ?? {
      process: proc.name,
      matched_rule: matched,
      action,
      blocked: false,
      terminated_pids: [],
    };

    if (action === 'block') {
      const killed = await terminatePid(proc.pid);
      if (killed) {
        entry.blocked = true;
        entry.terminated_pids.push(proc.pid);
      }
    }
    violations.set(key, entry);
  }

  return {
    checked_at: new Date().toISOString(),
    violations: [...violations.values()],
  };
}

/**
 * @returns {Promise<Array<{pid: number, name: string}>>}
 */
async function listProcessesNode() {
  if (process.platform !== 'linux') {
    return [];
  }
  const { readdir, readFile } = await import('node:fs/promises');
  const processes = [];
  try {
    const entries = await readdir('/proc');
    for (const pidStr of entries) {
      if (!/^\d+$/.test(pidStr)) continue;
      const pid = Number(pidStr);
      if (pid <= 1) continue;
      try {
        const comm = await readFile(`/proc/${pid}/comm`, 'utf8');
        processes.push({ pid, name: comm.trim() });
      } catch {
        // ignore
      }
    }
  } catch {
    return [];
  }
  return processes;
}

/**
 * @param {number} pid
 */
async function terminatePid(pid) {
  if (pid <= 1) return false;
  try {
    process.kill(pid, 'SIGTERM');
    return true;
  } catch {
    return false;
  }
}

function normalize(name) {
  return String(name).toLowerCase().replace(/\.exe$/, '');
}

function matchBlacklist(process, blacklist) {
  const norm = normalize(process);
  for (const rule of blacklist) {
    if (norm === normalize(rule)) {
      return rule;
    }
  }
  return null;
}

/**
 * Convert violations to cloud event payloads.
 * @param {Array<{process: string, matched_rule: string, action: string, blocked?: boolean, terminated_pids?: number[]}>} violations
 */
export function violationsToEvents(violations) {
  return violations.map((v) => ({
    event_type: v.blocked ? 'software.process_blocked' : 'software.blacklist_detected',
    severity: v.action === 'block' ? 'critical' : 'warning',
    detail: {
      process: v.process,
      matched_rule: v.matched_rule,
      action: v.action,
      blocked: Boolean(v.blocked),
      terminated_pids: v.terminated_pids ?? [],
    },
  }));
}
