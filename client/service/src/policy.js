import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const POLICY_FILE = path.resolve(__dirname, '../.state/policy.json');

export async function loadPolicyState() {
  try {
    const raw = await readFile(POLICY_FILE, 'utf8');
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** @param {object} policy */
export async function savePolicyState(policy) {
  await mkdir(path.dirname(POLICY_FILE), { recursive: true });
  await writeFile(POLICY_FILE, JSON.stringify(policy, null, 2));
}

/**
 * Sync policy bundle from cloud when heartbeat indicates a new hash.
 * @param {object} config
 * @param {string} clientId
 * @param {object | undefined} bundleSummary
 */
export async function syncPolicy(config, clientId, bundleSummary) {
  if (!bundleSummary?.hash || !clientId) {
    return loadPolicyState();
  }

  const local = await loadPolicyState();
  if (local?.hash === bundleSummary.hash) {
    return local;
  }

  const url = `${config.serverUrl}/api/client/v1/service/policy-bundle?client_id=${encodeURIComponent(clientId)}`;
  try {
    const res = await fetch(url);
    const body = await res.json();
    if (!res.ok || body?.code !== 0) {
      console.warn('[sentinel-service] policy-bundle fetch failed', body?.message ?? res.status);
      return local;
    }
    const policy = {
      version: body.data.version,
      hash: body.data.hash,
      published_at: body.data.published_at,
      rules: body.data.rules,
      synced_at: new Date().toISOString(),
    };
    await savePolicyState(policy);
    console.log(`[sentinel-service] policy updated v${policy.version}`);
    return policy;
  } catch (err) {
    console.error('[sentinel-service] policy sync failed:', err.message);
    return local;
  }
}
