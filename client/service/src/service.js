import { collectAssets } from './collectors/index.js';
import { resolveNativeBin } from './native-bridge.js';
import { LocalServer } from './local-server.js';
import { loadClientState, saveClientState } from './state.js';
import { loadPolicyState, syncPolicy } from './policy.js';
import { loadBaselineState, syncBaseline } from './compliance-baseline.js';
import { loadDlpState, syncDlpRules } from './dlp-policy.js';
import { loadNacState, syncNacPolicy } from './nac-policy.js';
import { enforceSoftware, violationsToEvents } from './enforcers/software.js';
import { enforceDlp, dlpViolationsToEvents } from './enforcers/dlp.js';
import { evaluateNac } from './enforcers/nac.js';
import { scanCompliance } from './collectors/compliance.js';

/**
 * PC client background service — orchestration layer.
 * Cloud API + local IPC + native sidecar dispatch.
 */
export class ClientService {
  /** @param {ReturnType<import('./config.js').loadConfig>} config */
  constructor(config) {
    this.config = config;
    this.heartbeatTimer = null;
    this.assetTimer = null;
    this.enforceTimer = null;
    this.complianceTimer = null;
    this.dlpTimer = null;
    this.nacTimer = null;
    this.running = false;
    /** @type {LocalServer | null} */
    this.localServer = null;
    /** @type {string | null} */
    this.nativeBin = null;
    this.state = {
      startedAt: null,
      lastHeartbeatAt: null,
      lastHeartbeatOk: false,
      lastAssetAt: null,
      cloudConnected: false,
      nativeAvailable: false,
      clientId: config.clientId,
    };
    /** @type {object | null} */
    this.assets = null;
    /** @type {object | null} */
    this.policy = null;
    /** @type {object | null} */
    this.complianceBaseline = null;
    /** @type {object | null} */
    this.dlpRules = null;
    /** @type {object | null} */
    this.nacPolicy = null;
    /** @type {object | null} */
    this.nacStatus = null;
    /** @type {number} */
    this.lastComplianceScore = 0;
    /** @type {Set<string>} */
    this.reportedViolations = new Set();
    /** @type {Set<string>} */
    this.reportedDlpViolations = new Set();
  }

  getLocalStatus() {
    return {
      running: this.running,
      version: this.config.version,
      client_id: this.state.clientId || null,
      cloud: {
        server_url: this.config.serverUrl,
        connected: this.state.cloudConnected,
        last_heartbeat_at: this.state.lastHeartbeatAt,
      },
      native: {
        available: this.state.nativeAvailable,
        bin: this.nativeBin,
      },
      assets: {
        collected_at: this.state.lastAssetAt,
        software_count: this.assets?.software?.length ?? 0,
      },
      policy: {
        version: this.policy?.version ?? null,
        hash: this.policy?.hash ?? null,
      },
      compliance_baseline: {
        id: this.complianceBaseline?.id ?? null,
        hash: this.complianceBaseline?.hash ?? null,
      },
      dlp: {
        rule_count: this.dlpRules?.rules?.length ?? 0,
        hash: this.dlpRules?.hash ?? null,
      },
      nac: {
        access_state: this.nacStatus?.access_state ?? null,
        compliance_score: this.lastComplianceScore,
      },
      started_at: this.state.startedAt,
    };
  }

  async start() {
    if (this.running) return;
    this.running = true;
    this.state.startedAt = new Date().toISOString();

    console.log(`[sentinel-service] starting v${this.config.version}`);
    console.log(`[sentinel-service] cloud: ${this.config.serverUrl}`);

    this.nativeBin = await resolveNativeBin(this.config.nativeBin);
    this.state.nativeAvailable = Boolean(this.nativeBin);
    if (this.nativeBin) {
      console.log(`[sentinel-service] native sidecar: ${this.nativeBin}`);
    } else {
      console.log('[sentinel-service] native sidecar not found, using Node collectors');
    }

    this.localServer = new LocalServer({
      host: this.config.localHost,
      port: this.config.localPort,
      getStatus: () => this.getLocalStatus(),
      getAssets: () => this.assets,
      getPolicy: () => this.policy,
    });
    await this.localServer.start();

    this.policy = await loadPolicyState();
    this.complianceBaseline = await loadBaselineState();
    this.dlpRules = await loadDlpState();
    this.nacPolicy = await loadNacState();

    const saved = await loadClientState();
    if (saved.client_id) {
      this.state.clientId = saved.client_id;
      this.config.clientId = saved.client_id;
    }

    await this.collectAndReportAssets();

    if (!this.state.clientId) {
      await this.register();
    }

    if (this.assets && this.state.clientId) {
      await this.reportAssets(this.assets);
    }

    await this.heartbeat();
    await this.runEnforcement();
    await this.runComplianceScan();
    await this.runDlpEnforcement();
    await this.runNacEvaluation();

    this.heartbeatTimer = setInterval(() => this.heartbeat(), this.config.heartbeatIntervalMs);
    this.assetTimer = setInterval(() => this.collectAndReportAssets(), this.config.assetCollectIntervalMs);
    this.enforceTimer = setInterval(() => this.runEnforcement(), this.config.enforceIntervalMs);
    this.complianceTimer = setInterval(() => this.runComplianceScan(), this.config.complianceIntervalMs);
    this.dlpTimer = setInterval(() => this.runDlpEnforcement(), this.config.dlpIntervalMs);
    this.nacTimer = setInterval(() => this.runNacEvaluation(), this.config.nacIntervalMs);
  }

  async stop() {
    this.running = false;
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.assetTimer) {
      clearInterval(this.assetTimer);
      this.assetTimer = null;
    }
    if (this.enforceTimer) {
      clearInterval(this.enforceTimer);
      this.enforceTimer = null;
    }
    if (this.complianceTimer) {
      clearInterval(this.complianceTimer);
      this.complianceTimer = null;
    }
    if (this.dlpTimer) {
      clearInterval(this.dlpTimer);
      this.dlpTimer = null;
    }
    if (this.nacTimer) {
      clearInterval(this.nacTimer);
      this.nacTimer = null;
    }
    if (this.localServer) {
      await this.localServer.stop();
      this.localServer = null;
    }
    console.log('[sentinel-service] stopped');
  }

  async register() {
    const url = `${this.config.serverUrl}/api/client/v1/service/register`;
    try {
      const hardware = this.assets?.hardware ?? {};
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tenant_token: this.config.tenantToken,
          version: this.config.version,
          client_id: this.config.clientId || undefined,
          hostname: hardware.hostname,
          os_type: hardware.os_type,
          os_version: hardware.os_version,
          hardware,
        }),
      });
      const body = await res.json();
      const clientId = body?.data?.client_id;
      if (clientId) {
        this.state.clientId = clientId;
        this.config.clientId = clientId;
        await saveClientState({ client_id: clientId });
        console.log(`[sentinel-service] registered client_id=${clientId}`);
      } else if (!res.ok) {
        console.error('[sentinel-service] register failed:', body?.message ?? res.status);
      }
    } catch (err) {
      console.error('[sentinel-service] register failed:', err.message);
    }
  }

  async heartbeat() {
    const url = `${this.config.serverUrl}/api/client/v1/service/heartbeat`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.state.clientId,
          version: this.config.version,
        }),
      });
      const body = await res.json();
      this.state.lastHeartbeatAt = new Date().toISOString();
      this.state.lastHeartbeatOk = res.ok;
      this.state.cloudConnected = res.ok;
      const bundleSummary = body?.data?.policy_bundle;
      if (bundleSummary && this.state.clientId) {
        this.policy = await syncPolicy(this.config, this.state.clientId, bundleSummary);
      }
      const baselineSummary = body?.data?.compliance_baseline;
      if (baselineSummary && this.state.clientId) {
        this.complianceBaseline = await syncBaseline(this.config, this.state.clientId, baselineSummary);
        await this.runComplianceScan();
      }
      const dlpSummary = body?.data?.dlp_rules;
      if (dlpSummary && this.state.clientId) {
        this.dlpRules = await syncDlpRules(this.config, this.state.clientId, dlpSummary);
        await this.runDlpEnforcement();
      }
      const nacSummary = body?.data?.nac_policy;
      if (nacSummary && this.state.clientId) {
        this.nacPolicy = await syncNacPolicy(this.config, this.state.clientId, nacSummary);
        await this.runNacEvaluation();
      }
      await this.runEnforcement();
      console.log('[sentinel-service] heartbeat ok', body?.data?.server_time ?? res.status);
    } catch (err) {
      this.state.lastHeartbeatAt = new Date().toISOString();
      this.state.lastHeartbeatOk = false;
      this.state.cloudConnected = false;
      console.error('[sentinel-service] heartbeat failed:', err.message);
    }
  }

  async collectAndReportAssets() {
    try {
      this.assets = await collectAssets(this.nativeBin);
      this.state.lastAssetAt = this.assets.collected_at;
      console.log(
        `[sentinel-service] assets collected (${this.assets.source}), software=${this.assets.software?.length ?? 0}`,
      );
      if (this.state.clientId) {
        await this.reportAssets(this.assets);
      }
    } catch (err) {
      console.error('[sentinel-service] asset collect failed:', err.message);
    }
  }

  async runComplianceScan() {
    if (!this.state.clientId) return;
    try {
      const report = await scanCompliance(this.nativeBin, this.complianceBaseline);
      this.lastComplianceScore = report.score ?? 0;
      console.log(`[sentinel-service] compliance scan score=${report.score} passed=${report.passed} failed=${report.failed}`);
      await this.reportCompliance(report);
      await this.runNacEvaluation();
    } catch (err) {
      console.error('[sentinel-service] compliance scan failed:', err.message);
    }
  }

  async reportCompliance(report) {
    const url = `${this.config.serverUrl}/api/client/v1/service/report/compliance`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.state.clientId,
          report,
        }),
      });
      if (!res.ok) {
        console.warn('[sentinel-service] compliance report status', res.status);
      }
    } catch (err) {
      console.error('[sentinel-service] compliance report failed:', err.message);
    }
  }

  async reportEvents(events) {
    if (!events.length || !this.state.clientId) return;
    const url = `${this.config.serverUrl}/api/client/v1/service/report/events`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.state.clientId,
          events,
        }),
      });
      if (!res.ok) {
        console.warn('[sentinel-service] event report status', res.status);
      } else {
        console.log(`[sentinel-service] reported ${events.length} event(s)`);
      }
    } catch (err) {
      console.error('[sentinel-service] event report failed:', err.message);
    }
  }

  async runEnforcement() {
    if (!this.policy || !this.state.clientId) return;
    try {
      const result = await enforceSoftware(this.nativeBin, this.policy);
      const newEvents = [];
      for (const v of result.violations ?? []) {
        const key = `${v.process}:${v.matched_rule}`;
        if (this.reportedViolations.has(key)) continue;
        this.reportedViolations.add(key);
        newEvents.push(...violationsToEvents([v]));
        if (v.blocked) {
          console.warn(`[sentinel-service] blocked process: ${v.process} pids=${(v.terminated_pids ?? []).join(',')}`);
        } else {
          console.warn(`[sentinel-service] software violation: ${v.process} (rule: ${v.matched_rule})`);
        }
      }
      if (newEvents.length > 0) {
        await this.reportEvents(newEvents);
      }
    } catch (err) {
      console.error('[sentinel-service] enforcement failed:', err.message);
    }
  }

  async runDlpEnforcement() {
    if (!this.dlpRules || !this.state.clientId) return;
    try {
      const result = await enforceDlp(this.nativeBin, this.dlpRules);
      const newEvents = [];
      for (const v of result.violations ?? []) {
        const key = `${v.rule_id}:${v.detail}`;
        if (this.reportedDlpViolations.has(key)) continue;
        this.reportedDlpViolations.add(key);
        newEvents.push(...dlpViolationsToEvents([v]));
        console.warn(`[sentinel-service] DLP violation: ${v.rule_name} (${v.channel})`);
      }
      if (newEvents.length > 0) {
        await this.reportEvents(newEvents);
      }
      await this.reportDlpEvidence(result.violations ?? []);
    } catch (err) {
      console.error('[sentinel-service] DLP enforcement failed:', err.message);
    }
  }

  async reportDlpEvidence(violations) {
    if (!violations.length || !this.state.clientId) return;
    const url = `${this.config.serverUrl}/api/client/v1/service/report/dlp-evidence`;
    for (const v of violations) {
      for (const file of v.evidence_files ?? []) {
        try {
          const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              client_id: this.state.clientId,
              evidence: {
                rule_id: v.rule_id,
                channel: v.channel,
                filename: file.filename,
                content_base64: file.content_base64,
                sha256: file.sha256,
              },
            }),
          });
          if (res.ok) {
            console.log(`[sentinel-service] DLP evidence uploaded: ${file.filename}`);
          }
        } catch (err) {
          console.error('[sentinel-service] DLP evidence upload failed:', err.message);
        }
      }
    }
  }

  async runNacEvaluation() {
    if (!this.nacPolicy || !this.state.clientId) return;
    try {
      const result = await evaluateNac(this.nativeBin, this.nacPolicy, this.lastComplianceScore);
      this.nacStatus = result;
      console.log(`[sentinel-service] NAC access=${result.access_state} score=${result.compliance_score}`);
      await this.reportNacStatus(result);
    } catch (err) {
      console.error('[sentinel-service] NAC evaluation failed:', err.message);
    }
  }

  async reportNacStatus(status) {
    const url = `${this.config.serverUrl}/api/client/v1/service/report/nac-status`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.state.clientId,
          status,
        }),
      });
      if (!res.ok) {
        console.warn('[sentinel-service] nac status report', res.status);
      }
    } catch (err) {
      console.error('[sentinel-service] nac status report failed:', err.message);
    }
  }

  /** @param {object} assets */
  async reportAssets(assets) {
    const url = `${this.config.serverUrl}/api/client/v1/service/report/assets`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.state.clientId,
          assets,
        }),
      });
      if (!res.ok) {
        console.warn('[sentinel-service] asset report status', res.status);
      }
    } catch (err) {
      console.error('[sentinel-service] asset report failed:', err.message);
    }
  }
}
