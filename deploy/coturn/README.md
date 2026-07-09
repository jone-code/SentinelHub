# Coturn TURN server (production remote assist)

WebRTC remote desktop often fails across NAT/firewalls without a **TURN relay**.
SentinelHub backend issues **time-limited TURN credentials** (TURN REST API) when
`REMOTE_TURN_SECRET` is configured.

## Quick start

```bash
cd deploy/coturn
export TURN_STATIC_AUTH_SECRET="$(openssl rand -hex 32)"
docker compose up -d
```

Configure the backend (same secret):

```bash
REMOTE_TURN_URL=turn:localhost:3478?transport=udp,turn:localhost:3478?transport=tcp
REMOTE_TURN_SECRET=<same as TURN_STATIC_AUTH_SECRET>
REMOTE_TURN_CREDENTIAL_TTL=86400
```

Restart the Spring Boot server. Console and PC client fetch ICE servers from
`/api/admin/v1/remote/rtc-config` and `/api/client/v1/service/remote/rtc-config`.

## Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 3478 | UDP/TCP | TURN/STUN |
| 5349 | UDP/TCP | TURN over TLS (optional) |
| 49152–49252 | UDP | Relay media |

Open these on the TURN host firewall. For cloud VMs, set `external-ip` in `turnserver.conf`.

## Credential model

| Mode | Backend env | Behavior |
|------|-------------|----------|
| **Production (recommended)** | `REMOTE_TURN_SECRET` | Ephemeral username `expiry:sentinel`, HMAC-SHA1 password |
| **Dev static** | `REMOTE_TURN_USERNAME` + `REMOTE_TURN_CREDENTIAL` | Fixed credentials in coturn `user=` entries |

Production uses coturn `use-auth-secret` + `static-auth-secret` (see `turnserver.conf`).

## Verify

```bash
# STUN/TURN reachability (install stuntman-client or use browser webrtc-internals)
turnutils_uclient -v -u test -w test turn://localhost:3478
```

Create a remote session in the console → **建立 WebRTC 连接** → check connection state `connected`.
If only `failed`, confirm TURN secret matches and relay ports are open.

## Security

- Use a strong random `TURN_STATIC_AUTH_SECRET` (32+ bytes)
- Restrict coturn to known relay port ranges
- Place TURN on a DMZ or dedicated host; do not expose admin APIs on the same host without TLS
- Prefer TLS TURN (`turns:`) in production when certificates are available
