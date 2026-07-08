import os from 'node:os';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

/**
 * P0 asset collection via Node (no native deps).
 * Deep capabilities (DLP, drivers) belong in client/native sidecar.
 */
export async function collectAssetsJs() {
  const hostname = os.hostname();
  const platform = os.platform();
  const release = os.release();
  const arch = os.arch();
  const cpus = os.cpus();
  const totalMemoryMb = Math.round(os.totalmem() / 1024 / 1024);

  const osType = platform === 'win32' ? 'windows'
    : platform === 'darwin' ? 'macos'
    : platform === 'linux' ? 'linux'
    : platform;

  const software = await listInstalledSoftware(platform);

  return {
    source: 'node',
    collected_at: new Date().toISOString(),
    hardware: {
      hostname,
      os_type: osType,
      os_version: release,
      arch,
      cpu_model: cpus[0]?.model ?? 'unknown',
      cpu_cores: cpus.length,
      memory_total_mb: totalMemoryMb,
    },
    software,
  };
}

/** @param {string} platform */
async function listInstalledSoftware(platform) {
  try {
    if (platform === 'win32') {
      const { stdout } = await execFileAsync('powershell', [
        '-NoProfile', '-Command',
        'Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | ' +
        'Select-Object DisplayName, DisplayVersion | ConvertTo-Json -Compress',
      ], { timeout: 10000, maxBuffer: 4 * 1024 * 1024 });
      const parsed = JSON.parse(stdout || '[]');
      const items = Array.isArray(parsed) ? parsed : [parsed];
      return items
        .filter((i) => i?.DisplayName)
        .slice(0, 50)
        .map((i) => ({ name: i.DisplayName, version: i.DisplayVersion ?? '' }));
    }

    if (platform === 'darwin') {
      const { stdout } = await execFileAsync('system_profiler', ['SPApplicationsDataType', '-json'], { timeout: 15000 });
      const data = JSON.parse(stdout);
      const apps = data?.SPApplicationsDataType ?? [];
      return apps
        .filter((a) => a._name)
        .slice(0, 50)
        .map((a) => ({ name: a._name, version: a.version ?? '' }));
    }

    if (platform === 'linux') {
      const { stdout } = await execFileAsync('sh', ['-c', 'dpkg-query -W -f=\'${Package}\\t${Version}\\n\' 2>/dev/null | head -50'], { timeout: 10000 });
      return stdout.trim().split('\n').filter(Boolean).map((line) => {
        const [name, version] = line.split('\t');
        return { name, version: version ?? '' };
      });
    }
  } catch (err) {
    console.warn('[sentinel-service] software inventory skipped:', err.message);
  }
  return [];
}
