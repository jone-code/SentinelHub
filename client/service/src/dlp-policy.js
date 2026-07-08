import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DLP_FILE = path.resolve(__dirname, '../.state/dlp-rules.json');

export async function loadDlpState() {
  try {
    const raw = await readFile(DLP_FILE, 'utf8');
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** @param {object} rules */
export async function saveDlpState(rules) {
  await mkdir(path.dirname(DLP_FILE), { recursive: true });
  await writeFile(DLP_FILE, JSON.stringify(rules, null, 2));
}

/**
 * @param {object} config
 * @param {string} clientId
 * @param {object | undefined} summary
 */
export async function syncDlpRules(config, clientId, summary) {
  if (!summary?.hash || !clientId) {
    return loadDlpState();
  }
  const local = await loadDlpState();
  if (local?.hash === summary.hash) {
    return local;
  }
  const url = `${config.serverUrl}/api/client/v1/service/dlp-rules?client_id=${encodeURIComponent(clientId)}`;
  try {
    const res = await fetch(url);
    const body = await res.json();
    if (!res.ok || body?.code !== 0) {
      console.warn('[sentinel-service] dlp-rules fetch failed', body?.message ?? res.status);
      return local;
    }
    const state = {
      hash: body.data.hash,
      updated_at: body.data.updated_at,
      rules: body.data.rules,
      synced_at: new Date().toISOString(),
    };
    await saveDlpState(state);
    console.log(`[sentinel-service] DLP rules updated (${state.rules?.length ?? 0} rules)`);
    return state;
  } catch (err) {
    console.error('[sentinel-service] DLP sync failed:', err.message);
    return local;
  }
}
