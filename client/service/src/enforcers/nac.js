import { writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { runNative } from '../native-bridge.js';

/**
 * @param {string | null} nativeBin
 * @param {object | null} nacPolicy
 * @param {number} complianceScore
 */
export async function evaluateNac(nativeBin, nacPolicy, complianceScore) {
  if (!nacPolicy) {
    return { access_state: 'unknown', compliance_score: complianceScore };
  }

  if (nativeBin) {
    const tmp = path.join(tmpdir(), `sentinel-nac-${process.pid}.json`);
    try {
      await writeFile(tmp, JSON.stringify(nacPolicy));
      return await runNative(nativeBin, [
        'enforce', 'nac', '--policy-file', tmp,
        '--compliance-score', String(complianceScore), '--json',
      ]);
    } catch (err) {
      console.warn('[sentinel-service] native nac failed, fallback:', err.message);
      return evaluateNacNode(nacPolicy, complianceScore);
    } finally {
      await unlink(tmp).catch(() => {});
    }
  }

  return evaluateNacNode(nacPolicy, complianceScore);
}

function evaluateNacNode(policy, complianceScore) {
  const min = policy.min_compliance_score ?? 80;
  if (policy.enabled === false) {
    return {
      evaluated_at: new Date().toISOString(),
      access_state: 'allowed',
      reason: 'policy_disabled',
      compliance_score: complianceScore,
      min_compliance_score: min,
    };
  }
  if (complianceScore >= min) {
    return {
      evaluated_at: new Date().toISOString(),
      access_state: 'allowed',
      reason: 'compliance_ok',
      compliance_score: complianceScore,
      min_compliance_score: min,
    };
  }
  const action = policy.action_on_fail ?? 'restrict';
  const accessState = action === 'deny' || action === 'denied' ? 'denied'
    : action === 'allow' ? 'allowed' : 'restricted';
  return {
    evaluated_at: new Date().toISOString(),
    access_state: accessState,
    reason: 'compliance_below_threshold',
    compliance_score: complianceScore,
    min_compliance_score: min,
    action_on_fail: action,
    isolate_vlan: policy.isolate_vlan ?? null,
  };
}
