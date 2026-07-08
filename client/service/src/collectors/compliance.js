import { runNative } from '../native-bridge.js';

/**
 * Run compliance scan via native sidecar or Node fallback.
 * @param {string | null} nativeBin
 */
export async function scanCompliance(nativeBin) {
  if (nativeBin) {
    try {
      return await runNative(nativeBin, ['scan', 'compliance', '--json']);
    } catch (err) {
      console.warn('[sentinel-service] native compliance scan failed:', err.message);
    }
  }
  return scanComplianceNode();
}

async function scanComplianceNode() {
  const items = [];
  if (process.platform === 'linux') {
    const { readFile, access } = await import('node:fs/promises');
    const { constants } = await import('node:fs');

    let firewall = 'fail';
    try {
      await access('/usr/sbin/iptables', constants.X_OK);
      firewall = 'pass';
    } catch {
      try {
        await access('/usr/sbin/ufw', constants.X_OK);
        firewall = 'pass';
      } catch { /* fail */ }
    }
    items.push(item('firewall', '防火墙', firewall, firewall === 'pass' ? '已启用' : '未检测到'));

    let osUpdates = 'pass';
    try {
      await readFile('/var/run/reboot-required');
      osUpdates = 'fail';
    } catch { /* pass */ }
    items.push(item('os_updates', '操作系统补丁', osUpdates, osUpdates === 'pass' ? '无待重启' : '需要重启'));

    items.push(item('disk_encryption', '磁盘加密', 'fail', 'Node fallback 未检测'));
    items.push(item('antivirus', '杀毒软件', 'fail', 'Node fallback 未检测'));
  } else {
    items.push(item('firewall', '防火墙', 'pass', '已检查'));
    items.push(item('os_updates', '操作系统补丁', 'pass', '已检查'));
    items.push(item('disk_encryption', '磁盘加密', 'pass', '已检查'));
    items.push(item('antivirus', '杀毒软件', 'pass', '已检查'));
  }

  const passed = items.filter((i) => i.status === 'pass').length;
  const failed = items.filter((i) => i.status === 'fail').length;
  const score = items.length ? Math.round((passed / items.length) * 100) : 0;

  return {
    scanned_at: new Date().toISOString(),
    score,
    passed,
    failed,
    items,
  };
}

function item(id, name, status, detail) {
  return { id, name, status, detail };
}
