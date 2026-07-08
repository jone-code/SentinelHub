/**
 * Handle remote assistance commands from heartbeat.
 */

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
