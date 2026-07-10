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
    enforceIntervalMs: parseInt(process.env.CLIENT_ENFORCE_INTERVAL_SEC || '60', 10) * 1000,
    complianceIntervalMs: parseInt(process.env.CLIENT_COMPLIANCE_INTERVAL_SEC || '300', 10) * 1000,
    dlpIntervalMs: parseInt(process.env.CLIENT_DLP_INTERVAL_SEC || '120', 10) * 1000,
    nacIntervalMs: parseInt(process.env.CLIENT_NAC_INTERVAL_SEC || '120', 10) * 1000,
    driverEventIntervalMs: parseInt(process.env.CLIENT_DRIVER_EVENT_INTERVAL_SEC || '3', 10) * 1000,
    driverBin: process.env.SENTINEL_DRIVER_BIN || '',
    remoteCaptureWidth: parseInt(process.env.REMOTE_CAPTURE_WIDTH || '1280', 10),
    remoteCaptureHeight: parseInt(process.env.REMOTE_CAPTURE_HEIGHT || '720', 10),
    remoteCaptureFps: parseInt(process.env.REMOTE_CAPTURE_FPS || '10', 10),
    remoteCaptureSynthetic: process.env.REMOTE_CAPTURE_SYNTHETIC === 'true',
    remoteCaptureBackend: process.env.REMOTE_CAPTURE_BACKEND || 'auto',
  };
}
