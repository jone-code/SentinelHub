import { loadConfig } from './config.js';

/**
 * PC client background service — register, heartbeat, report to cloud API.
 */
export class ClientService {
  /** @param {ReturnType<typeof loadConfig>} config */
  constructor(config) {
    this.config = config;
    this.timer = null;
    this.running = false;
  }

  async start() {
    if (this.running) return;
    this.running = true;
    console.log(`[sentinel-service] starting v${this.config.version}`);
    console.log(`[sentinel-service] server: ${this.config.serverUrl}`);

    await this.heartbeat();
    this.timer = setInterval(() => this.heartbeat(), this.config.heartbeatIntervalMs);
  }

  stop() {
    this.running = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    console.log('[sentinel-service] stopped');
  }

  async heartbeat() {
    const url = `${this.config.serverUrl}/api/client/v1/service/heartbeat`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: this.config.clientId,
          version: this.config.version,
        }),
      });
      const body = await res.json();
      console.log(`[sentinel-service] heartbeat ok`, body?.data?.server_time ?? res.status);
    } catch (err) {
      console.error('[sentinel-service] heartbeat failed:', err.message);
    }
  }
}
