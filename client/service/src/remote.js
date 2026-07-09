/**
 * Handle remote assistance commands from heartbeat.
 */

import { startRemoteWebRtc } from './remote-webrtc.js';

/**
 * @param {import('../config.js').loadConfig extends Function ? ReturnType<import('../config.js').loadConfig> : any} config
 * @param {string} clientId
 * @param {object} command
 */
export async function handleRemoteCommand(config, clientId, command) {
  const sessionId = command.session_id;
  if (!sessionId) return null;

  const consentUrl = `${config.serverUrl}/api/client/v1/service/remote/consent`;
  const res = await fetch(consentUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      client_id: clientId,
      session_id: sessionId,
      accepted: true,
    }),
  });
  const body = await res.json();
  console.log(
    `[sentinel-service] remote session ${sessionId} accepted (operator=${command.operator_name ?? 'unknown'})`,
  );
  try {
    await uploadRecordingStub(config, clientId, sessionId);
    startRemoteWebRtc(config, clientId, sessionId).catch((err) => {
      console.warn('[sentinel-service] remote WebRTC failed:', err.message);
    });
  } catch (err) {
    console.warn('[sentinel-service] remote recording upload failed:', err.message);
  }
  return body?.data ?? null;
}

/**
 * @param {import('../config.js').loadConfig extends Function ? ReturnType<import('../config.js').loadConfig> : any} config
 * @param {string} clientId
 * @param {string} sessionId
 * @param {string} [recordingKey]
 */
export async function endRemoteSession(config, clientId, sessionId, recordingKey) {
  const url = `${config.serverUrl}/api/client/v1/service/remote/status`;
  await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      client_id: clientId,
      session_id: sessionId,
      status: 'ended',
      recording_key: recordingKey ?? null,
    }),
  });
}

export async function uploadRecordingStub(config, clientId, sessionId) {
  const metadata = JSON.stringify({
    session_id: sessionId,
    recorded_at: new Date().toISOString(),
    note: 'WebRTC session metadata',
  });
  const contentBase64 = Buffer.from(metadata, 'utf8').toString('base64');
  const url = `${config.serverUrl}/api/client/v1/service/remote/recording`;
  await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      client_id: clientId,
      session_id: sessionId,
      content_base64: contentBase64,
      content_type: 'application/json',
    }),
  });
}
