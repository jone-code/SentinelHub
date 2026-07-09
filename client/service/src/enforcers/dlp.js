import { writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runNative } from '../native-bridge.js';

/**
 * @param {string | null} nativeBin
 * @param {object | null} dlpState
 */
export async function enforceDlp(nativeBin, dlpState) {
  const rules = dlpState?.rules ?? [];
  if (rules.length === 0) {
    return { checked_at: new Date().toISOString(), violations: [] };
  }

  if (nativeBin) {
    const tmp = path.join(tmpdir(), `sentinel-dlp-${process.pid}.json`);
    try {
      await writeFile(tmp, JSON.stringify({ rules }));
      return await runNative(nativeBin, ['enforce', 'dlp', '--rules-file', tmp, '--json']);
    } catch (err) {
      console.warn('[sentinel-service] native dlp failed, fallback:', err.message);
      return enforceDlpNode(rules);
    } finally {
      await unlink(tmp).catch(() => {});
    }
  }

  return enforceDlpNode(rules);
}

/**
 * @param {Array<object>} rules
 */
async function enforceDlpNode(rules) {
  const violations = [];
  for (const rule of rules) {
    if (rule.channel === 'usb' && process.platform === 'linux') {
      const mounts = await listUsbMountsNode();
      if (mounts.length > 0) {
        violations.push({
          rule_id: rule.id,
          rule_name: rule.name,
          channel: rule.channel,
          action: rule.action,
          detail: `检测到 USB 挂载: ${mounts.join(', ')}`,
          blocked: false,
        });
      }
    }
  }
  return { checked_at: new Date().toISOString(), violations };
}

async function listUsbMountsNode() {
  const { readFile } = await import('node:fs/promises');
  const mounts = [];
  try {
    const proc = await readFile('/proc/mounts', 'utf8');
    for (const line of proc.split('\n')) {
      const cols = line.split(/\s+/);
      if (cols[1] && (cols[1].includes('/media/') || cols[1].includes('/mnt/'))) {
        mounts.push(cols[1]);
      }
    }
  } catch {
    return [];
  }
  return mounts;
}

/**
 * @param {Array<{rule_id: string, rule_name: string, channel: string, action: string, detail: string, blocked?: boolean}>} violations
 */
export function dlpViolationsToEvents(violations) {
  return violations.map((v) => ({
    event_type: v.blocked ? 'dlp.blocked' : 'dlp.violation',
    severity: v.action === 'block' ? 'critical' : 'warning',
    detail: {
      rule_id: v.rule_id,
      rule_name: v.rule_name,
      channel: v.channel,
      action: v.action,
      detail: v.detail,
      blocked: Boolean(v.blocked),
    },
  }));
}
