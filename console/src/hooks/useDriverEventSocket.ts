import { useEffect, useRef } from 'react';
import { getToken } from '../api/client';

export interface DriverEventPayload {
  id: string;
  event_type: string;
  severity: string;
  hostname?: string;
  agent_id?: string;
  detail: Record<string, unknown>;
  created_at: string;
}

interface WsEnvelope {
  type: string;
  data?: DriverEventPayload;
}

function wsUrl(token: string): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}/api/admin/v1/ws/events?token=${encodeURIComponent(token)}`;
}

export function useDriverEventSocket(
  enabled: boolean,
  onEvent: (event: DriverEventPayload) => void,
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    const token = getToken();
    if (!token) {
      return undefined;
    }

    let ws: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let closed = false;

    const connect = () => {
      if (closed) return;
      ws = new WebSocket(wsUrl(token));
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data as string) as WsEnvelope;
          if (msg.type === 'driver.event' && msg.data) {
            onEventRef.current(msg.data);
          }
        } catch {
          // ignore malformed frames
        }
      };
      ws.onclose = () => {
        if (!closed) {
          reconnectTimer = setTimeout(connect, 3000);
        }
      };
    };

    connect();

    return () => {
      closed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      ws?.close();
    };
  }, [enabled]);
}
