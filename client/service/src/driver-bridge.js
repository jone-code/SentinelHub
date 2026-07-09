/**
 * Sentinel driver daemon bridge — start daemon and query status via native sidecar.
 */
import { spawn } from 'node:child_process';
import { access } from 'node:fs/promises';
import { constants } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runNative } from './native-bridge.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const DEFAULT_SOCKET = process.env.SENTINEL_DRIVER_SOCKET || '/tmp/sentinel-driver.sock';

/**
 * @param {string | undefined} configuredPath
 */
export async function resolveDriverBin(configuredPath) {
  const candidates = [];
  if (configuredPath) candidates.push(configuredPath);

  const release = path.resolve(__dirname, '../../native/driver/sentinel-driver/target/release/sentinel-driver');
  const debug = path.resolve(__dirname, '../../native/driver/sentinel-driver/target/debug/sentinel-driver');
  const releaseExe = process.platform === 'win32' ? `${release}.exe` : release;
  candidates.push(releaseExe, release, debug);

  for (const bin of candidates) {
    try {
      await access(bin, constants.X_OK);
      return bin;
    } catch {
      // try next
    }
  }
  return null;
}

/**
 * Start driver daemon in background if not already reachable.
 * @param {string | null} driverBin
 * @param {string | null} nativeBin
 */
export async function ensureDriverDaemon(driverBin, nativeBin) {
  if (!driverBin || process.platform === 'win32') {
    return false;
  }
  const status = await queryDriverViaNative(nativeBin);
  if (status?.available) {
    return true;
  }
  try {
    const child = spawn(driverBin, [], {
      detached: true,
      stdio: 'ignore',
      env: { ...process.env, SENTINEL_DRIVER_SOCKET: DEFAULT_SOCKET },
    });
    child.unref();
    await new Promise((r) => setTimeout(r, 300));
    const after = await queryDriverViaNative(nativeBin);
    return Boolean(after?.available);
  } catch (err) {
    console.warn('[sentinel-service] driver daemon start failed:', err.message);
    return false;
  }
}

/**
 * @param {string | null} nativeBin
 */
export async function queryDriverViaNative(nativeBin) {
  if (!nativeBin) return null;
  try {
    return await runNative(nativeBin, ['driver', 'status', '--json']);
  } catch (err) {
    console.warn('[sentinel-service] driver status failed:', err.message);
    return null;
  }
}
