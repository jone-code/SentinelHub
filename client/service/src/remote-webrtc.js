/**
 * WebRTC answerer for remote assist — uses @roamhq/wrtc in Node.js.
 */

let wrtcModule = null;

async function loadWrtc() {
  if (!wrtcModule) {
    wrtcModule = await import('@roamhq/wrtc');
  }
  return wrtcModule.default ?? wrtcModule;
}

async function fetchRtcConfig(config) {
  const res = await fetch(`${config.serverUrl}/api/client/v1/service/remote/rtc-config`);
  const body = await res.json();
  return body?.data?.ice_servers ?? [{ urls: 'stun:stun.l.google.com:19302' }];
}

async function pollAdminOffer(config, clientId, sessionId, maxAttempts = 30) {
  const url = `${config.serverUrl}/api/client/v1/service/remote/signal?client_id=${encodeURIComponent(clientId)}&session_id=${encodeURIComponent(sessionId)}`;
  for (let i = 0; i < maxAttempts; i++) {
    const res = await fetch(url);
    const body = await res.json();
    const data = body?.data;
    if (data?.sdp_payload && data?.sdp_type === 'offer') {
      return data.sdp_payload;
    }
    await sleep(2000);
  }
  return null;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Start WebRTC peer as answerer with synthetic video feed.
 * @param {object} config
 * @param {string} clientId
 * @param {string} sessionId
 */
export async function startRemoteWebRtc(config, clientId, sessionId) {
  const offerSdp = await pollAdminOffer(config, clientId, sessionId, 15);
  if (!offerSdp) {
    console.log('[sentinel-service] remote WebRTC: no admin offer yet');
    return null;
  }

  const wrtc = await loadWrtc();
  const { RTCPeerConnection, RTCSessionDescription, nonstandard } = wrtc;
  const iceServers = await fetchRtcConfig(config);
  const pc = new RTCPeerConnection({ iceServers });

  const appliedAdminIce = new Set();

  pc.onicecandidate = async (event) => {
    if (!event.candidate) return;
    await fetch(`${config.serverUrl}/api/client/v1/service/remote/ice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        client_id: clientId,
        session_id: sessionId,
        candidate: event.candidate.toJSON(),
      }),
    });
  };

  // Synthetic video source (test pattern)
  if (nonstandard?.RTCVideoSource) {
    const source = new nonstandard.RTCVideoSource();
    const track = source.createTrack();
    pc.addTrack(track);
    let frame = 0;
    const timer = setInterval(() => {
      try {
        const width = 640;
        const height = 480;
        const rgba = new Uint8ClampedArray(width * height * 4);
        for (let i = 0; i < width * height; i++) {
          const o = i * 4;
          rgba[o] = (frame + i) % 256;
          rgba[o + 1] = 100;
          rgba[o + 2] = 200;
          rgba[o + 3] = 255;
        }
        source.onFrame({ width, height, data: rgba });
        frame += 1;
      } catch {
        clearInterval(timer);
      }
    }, 100);
    pc.addEventListener('connectionstatechange', () => {
      if (pc.connectionState === 'closed' || pc.connectionState === 'failed') {
        clearInterval(timer);
      }
    });
  }

  await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: offerSdp }));

  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);

  await fetch(`${config.serverUrl}/api/client/v1/service/remote/signal`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      client_id: clientId,
      session_id: sessionId,
      sdp_type: 'answer',
      sdp_payload: answer.sdp,
    }),
  });

  // Poll admin ICE candidates
  const icePoll = setInterval(async () => {
    if (pc.connectionState === 'connected' || pc.connectionState === 'failed' || pc.connectionState === 'closed') {
      clearInterval(icePoll);
      return;
    }
    const url = `${config.serverUrl}/api/client/v1/service/remote/ice?client_id=${encodeURIComponent(clientId)}&session_id=${encodeURIComponent(sessionId)}`;
    const res = await fetch(url);
    const body = await res.json();
    const items = body?.data ?? [];
    for (const item of items) {
      const id = item.id;
      if (appliedAdminIce.has(id)) continue;
      appliedAdminIce.add(id);
      const c = item.candidate;
      if (c) {
        try {
          await pc.addIceCandidate(c);
        } catch (err) {
          console.warn('[sentinel-service] addIceCandidate failed:', err.message);
        }
      }
    }
  }, 2000);

  console.log(`[sentinel-service] remote WebRTC answer posted for ${sessionId}`);
  return { connection: pc, sessionId };
}
