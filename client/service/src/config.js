/**
 * Load configuration from environment variables.
 */
export function loadConfig() {
  const intervalSec = parseInt(process.env.CLIENT_HEARTBEAT_INTERVAL_SEC || '60', 10);
  const localPort = parseInt(process.env.CLIENT_LOCAL_PORT || '39201', 10);
  return {
    serverUrl: process.env.CLIENT_SERVER_URL || 'http://localhost:8080',
    clientId: process.env.CLIENT_ID || '',
    tenantToken: process.env.CLIENT_TENANT_TOKEN || 'demo-token',
    version: process.env.CLIENT_VERSION || '0.1.0-dev',
    heartbeatIntervalMs: intervalSec * 1000,
    localHost: process.env.CLIENT_LOCAL_HOST || '127.0.0.1',
    localPort,
    nativeBin: process.env.SENTINEL_NATIVE_BIN || '',
    assetCollectIntervalMs: parseInt(process.env.CLIENT_ASSET_INTERVAL_SEC || '300', 10) * 1000,
  };
}
