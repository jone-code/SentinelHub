import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const NAC_FILE = path.resolve(__dirname, '../.state/nac-policy.json');

export async function loadNacState() {
  try {
    const raw = await readFile(NAC_FILE, 'utf8');
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** @param {object} policy */
export async function saveNacState(policy) {
  await mkdir(path.dirname(NAC_FILE), { recursive: true });
  await writeFile(NAC_FILE, JSON.stringify(policy, null, 2));
}

/**
 * @param {object} config
 * @param {string} clientId
 * @param {object | undefined} summary
 */
export async function syncNacPolicy(config, clientId, summary) {
  if (!summary?.hash || !clientId) {
    return loadNacState();
  }
  const local = await loadNacState();
  if (local?.hash === summary.hash) {
    return local;
  }
  const url = `${config.serverUrl}/api/client/v1/service/nac-policy?client_id=${encodeURIComponent(clientId)}`;
  try {
    const res = await fetch(url);
    const body = await res.json();
    if (!res.ok || body?.code !== 0) {
      console.warn('[sentinel-service] nac-policy fetch failed', body?.message ?? res.status);
      return local;
    }
    const state = { ...body.data, synced_at: new Date().toISOString() };
    await saveNacState(state);
    console.log('[sentinel-service] NAC policy updated');
    return state;
  } catch (err) {
    console.error('[sentinel-service] NAC sync failed:', err.message);
    return local;
  }
}
