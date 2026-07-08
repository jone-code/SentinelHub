import { spawn } from 'node:child_process';
import { access } from 'node:fs/promises';
import { constants } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Resolve sentinel-native sidecar binary.
 * Priority: SENTINEL_NATIVE_BIN env → ../../native/target/release/sentinel-native
 */
export async function resolveNativeBin(configuredPath) {
  const candidates = [];
  if (configuredPath) candidates.push(configuredPath);

  const repoBin = path.resolve(__dirname, '../../native/target/release/sentinel-native');
  const repoBinExe = process.platform === 'win32' ? `${repoBin}.exe` : repoBin;
  candidates.push(repoBinExe, repoBin);

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
 * Run sentinel-native with args; returns parsed JSON stdout or throws.
 * @param {string} bin
 * @param {string[]} args
 * @param {number} timeoutMs
 */
export function runNative(bin, args, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const child = spawn(bin, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';

    const timer = setTimeout(() => {
      child.kill('SIGTERM');
      reject(new Error(`native timeout after ${timeoutMs}ms`));
    }, timeoutMs);

    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });

    child.on('error', (err) => {
      clearTimeout(timer);
      reject(err);
    });

    child.on('close', (code) => {
      clearTimeout(timer);
      if (code !== 0) {
        reject(new Error(stderr.trim() || `native exited with code ${code}`));
        return;
      }
      try {
        resolve(JSON.parse(stdout));
      } catch {
        reject(new Error(`invalid native JSON: ${stdout.slice(0, 200)}`));
      }
    });
  });
}

/**
 * @param {string | null} bin
 */
export async function collectViaNative(bin) {
  if (!bin) return null;
  try {
    return await runNative(bin, ['collect', '--json']);
  } catch (err) {
    console.warn('[sentinel-service] native collect failed:', err.message);
    return null;
  }
}
