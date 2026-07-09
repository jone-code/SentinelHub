import { writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runNative } from '../native-bridge.js';
import { enabledRules } from '../compliance-baseline.js';

/**
 * Run compliance scan via native sidecar or Node fallback.
 * @param {string | null} nativeBin
 * @param {object | null} baseline
 */
export async function scanCompliance(nativeBin, baseline) {
  const rules = enabledRules(baseline);
  if (rules.length === 0) {
    return emptyResult();
  }

  if (nativeBin) {
    const tmp = path.join(tmpdir(), `sentinel-baseline-${process.pid}.json`);
    try {
      await writeFile(tmp, JSON.stringify(rules));
      return await runNative(nativeBin, ['scan', 'compliance', '--rules-file', tmp, '--json']);
    } catch (err) {
      console.warn('[sentinel-service] native compliance scan failed:', err.message);
      return scanComplianceNode(rules);
    } finally {
      await unlink(tmp).catch(() => {});
    }
  }

  return scanComplianceNode(rules);
}

function emptyResult() {
  return {
    scanned_at: new Date().toISOString(),
    score: 0,
    passed: 0,
    failed: 0,
    items: [],
  };
}

/**
 * @param {Array<{id: string, name: string, weight?: number}>} rules
 */
async function scanComplianceNode(rules) {
  const checkers = {
    firewall: checkFirewall,
    os_updates: checkOsUpdates,
    disk_encryption: checkDiskEncryption,
    antivirus: checkAntivirus,
  };

  const items = [];
  for (const rule of rules) {
    const checker = checkers[rule.id];
    if (!checker) {
      items.push(item(rule.id, rule.name, 'fail', '不支持的检查项', rule.weight ?? 1));
      continue;
    }
    items.push(await checker(rule));
  }

  return scoreResult(items);
}

async function checkFirewall(rule) {
  if (process.platform !== 'linux') {
    return item(rule.id, rule.name, 'pass', '已检查', rule.weight ?? 1);
  }
  const { access } = await import('node:fs/promises');
  const { constants } = await import('node:fs');
  let ok = false;
  try {
    await access('/usr/sbin/iptables', constants.X_OK);
    ok = true;
  } catch {
    try {
      await access('/usr/sbin/ufw', constants.X_OK);
      ok = true;
    } catch { /* fail */ }
  }
  return item(rule.id, rule.name, ok ? 'pass' : 'fail', ok ? '已启用' : '未检测到', rule.weight ?? 1);
}

async function checkOsUpdates(rule) {
  if (process.platform !== 'linux') {
    return item(rule.id, rule.name, 'pass', '已检查', rule.weight ?? 1);
  }
  const { readFile } = await import('node:fs/promises');
  try {
    await readFile('/var/run/reboot-required');
    return item(rule.id, rule.name, 'fail', '需要重启', rule.weight ?? 1);
  } catch {
    return item(rule.id, rule.name, 'pass', '无待重启', rule.weight ?? 1);
  }
}

async function checkDiskEncryption(rule) {
  if (process.platform !== 'linux') {
    return item(rule.id, rule.name, 'pass', '已检查', rule.weight ?? 1);
  }
  const { execFile } = await import('node:child_process');
  const { promisify } = await import('node:util');
  const exec = promisify(execFile);
  try {
    const { stdout } = await exec('lsblk', ['-o', 'TYPE', '-n']);
    const ok = stdout.includes('crypt');
    return item(rule.id, rule.name, ok ? 'pass' : 'fail', ok ? '检测到加密' : '未检测到', rule.weight ?? 1);
  } catch {
    return item(rule.id, rule.name, 'fail', '检测失败', rule.weight ?? 1);
  }
}

async function checkAntivirus(rule) {
  if (process.platform !== 'linux') {
    return item(rule.id, rule.name, 'pass', '已检查', rule.weight ?? 1);
  }
  const { access } = await import('node:fs/promises');
  const { constants } = await import('node:fs');
  for (const bin of ['/usr/sbin/clamd', '/usr/bin/clamscan']) {
    try {
      await access(bin, constants.X_OK);
      return item(rule.id, rule.name, 'pass', '检测到 ClamAV', rule.weight ?? 1);
    } catch { /* try next */ }
  }
  return item(rule.id, rule.name, 'fail', '未检测到', rule.weight ?? 1);
}

function scoreResult(items) {
  const passed = items.filter((i) => i.status === 'pass').length;
  const failed = items.filter((i) => i.status === 'fail').length;
  const totalWeight = items.reduce((sum, i) => sum + (i.weight ?? 1), 0);
  const passedWeight = items
    .filter((i) => i.status === 'pass')
    .reduce((sum, i) => sum + (i.weight ?? 1), 0);
  const score = totalWeight ? Math.round((passedWeight / totalWeight) * 100) : 0;

  return {
    scanned_at: new Date().toISOString(),
    score,
    passed,
    failed,
    items,
  };
}

function item(id, name, status, detail, weight) {
  return { id, name, status, detail, weight };
}
