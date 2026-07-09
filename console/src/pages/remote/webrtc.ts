import { useCallback, useRef, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface IceServer {
  urls: string | string[];
  username?: string;
  credential?: string;
}

interface RtcConfig {
  ice_servers: IceServer[];
}

interface IceItem {
  id: string;
  candidate: RTCIceCandidateInit;
}

export function useRemoteWebRtc(sessionId: string | null) {
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const appliedIceRef = useRef<Set<string>>(new Set());
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [connectionState, setConnectionState] = useState<string>('new');

  const cleanup = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }
    appliedIceRef.current.clear();
    setConnectionState('closed');
  }, []);

  const connect = useCallback(async () => {
    if (!sessionId) return;
    cleanup();

    const configRes = await api.get<ApiEnvelope<RtcConfig>>('/remote/rtc-config');
    const iceServers = configRes.data.data.ice_servers;

    const pc = new RTCPeerConnection({ iceServers });
    pcRef.current = pc;

    pc.onconnectionstatechange = () => setConnectionState(pc.connectionState);
    pc.ontrack = (event) => {
      if (videoRef.current && event.streams[0]) {
        videoRef.current.srcObject = event.streams[0];
      }
    };
    pc.onicecandidate = async (event) => {
      if (!event.candidate) return;
      await api.post(`/remote/sessions/${sessionId}/ice`, {
        candidate: event.candidate.toJSON(),
      });
    };

    pc.addTransceiver('video', { direction: 'recvonly' });

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    await api.post(`/remote/sessions/${sessionId}/signal`, {
      sdp_type: 'offer',
      sdp_payload: offer.sdp,
    });

    pollRef.current = setInterval(async () => {
      if (!pcRef.current || pc.connectionState === 'connected' || pc.connectionState === 'failed') {
        return;
      }
      try {
        const ansRes = await api.get<ApiEnvelope<{ sdp_payload?: string; sdp_type?: string }>>(
          `/remote/sessions/${sessionId}/signaling`,
        );
        const ans = ansRes.data.data;
        if (ans?.sdp_payload && ans.sdp_type === 'answer' && !pc.remoteDescription) {
          await pc.setRemoteDescription({ type: 'answer', sdp: ans.sdp_payload });
        }

        const iceRes = await api.get<ApiEnvelope<IceItem[]>>(`/remote/sessions/${sessionId}/ice`);
        for (const item of iceRes.data.data ?? []) {
          if (appliedIceRef.current.has(item.id)) continue;
          appliedIceRef.current.add(item.id);
          if (item.candidate) {
            await pc.addIceCandidate(item.candidate);
          }
        }
      } catch {
        // keep polling
      }
    }, 2000);
  }, [sessionId, cleanup]);

  return { connect, cleanup, videoRef, connectionState };
}
