import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASELINE_FILE = path.resolve(__dirname, '../.state/compliance-baseline.json');

export async function loadBaselineState() {
  try {
    const raw = await readFile(BASELINE_FILE, 'utf8');
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** @param {object} baseline */
export async function saveBaselineState(baseline) {
  await mkdir(path.dirname(BASELINE_FILE), { recursive: true });
  await writeFile(BASELINE_FILE, JSON.stringify(baseline, null, 2));
}

/**
 * Sync compliance baseline from cloud when heartbeat indicates a new hash.
 * @param {object} config
 * @param {string} clientId
 * @param {object | undefined} baselineSummary
 */
export async function syncBaseline(config, clientId, baselineSummary) {
  if (!baselineSummary?.hash || !clientId) {
    return loadBaselineState();
  }

  const local = await loadBaselineState();
  if (local?.hash === baselineSummary.hash) {
    return local;
  }

  const url = `${config.serverUrl}/api/client/v1/service/compliance-baseline?client_id=${encodeURIComponent(clientId)}`;
  try {
    const res = await fetch(url);
    const body = await res.json();
    if (!res.ok || body?.code !== 0) {
      console.warn('[sentinel-service] compliance-baseline fetch failed', body?.message ?? res.status);
      return local;
    }
    const baseline = {
      id: body.data.id,
      name: body.data.name,
      hash: body.data.hash,
      updated_at: body.data.updated_at,
      rules: body.data.rules,
      synced_at: new Date().toISOString(),
    };
    await saveBaselineState(baseline);
    console.log(`[sentinel-service] compliance baseline updated (${baseline.name})`);
    return baseline;
  } catch (err) {
    console.error('[sentinel-service] compliance baseline sync failed:', err.message);
    return local;
  }
}

/**
 * @param {object | null} baseline
 */
export function enabledRules(baseline) {
  const rules = baseline?.rules ?? [];
  return rules.filter((r) => r.enabled !== false);
}
