/**
 * Load configuration from environment variables.
 */
export function loadConfig() {
  const intervalSec = parseInt(process.env.CLIENT_HEARTBEAT_INTERVAL_SEC || '60', 10);
  return {
    serverUrl: process.env.CLIENT_SERVER_URL || 'http://localhost:8080',
    clientId: process.env.CLIENT_ID || '',
    tenantToken: process.env.CLIENT_TENANT_TOKEN || '',
    version: process.env.CLIENT_VERSION || '0.1.0-dev',
    heartbeatIntervalMs: intervalSec * 1000,
  };
}
