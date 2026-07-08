# FreeRADIUS integration template (P2)

SentinelHub NAC can push VLAN assignment hints to network gear via RADIUS.
This directory provides a **test-environment** FreeRADIUS skeleton aligned with `nac_radius_settings`.

## Quick start

```bash
cd deploy/radius
docker compose up -d
```

Default ports:
- RADIUS auth: `1812/udp`
- RADIUS acct: `1813/udp`

## Mapping compliance → VLAN

Configure in console **网络准入 → RADIUS 集成** or `nac_radius_settings`:

| access_state | VLAN field |
|--------------|------------|
| `allowed` | `vlan_allowed` (e.g. `vlan-prod`) |
| `restricted` | `vlan_restricted` (e.g. `vlan-quarantine`) |
| `denied` | `vlan_denied` (e.g. `vlan-deny`) |

The PC client reports `access_state` after compliance evaluation. A production deployment
uses a RADIUS policy server or NAS script to assign VLANs based on SentinelHub device state.

## Files

- `docker-compose.yml` — FreeRADIUS container
- `config/clients.conf` — NAS clients (switch/AP shared secret)
- `config/users` — example user/device entries

## Console API

- `GET /api/admin/v1/nac/radius` — read settings (secret masked)
- `PUT /api/admin/v1/nac/radius` — update settings

Client fetch (when enabled):

- `GET /api/client/v1/service/nac-radius?client_id=...`

## Security notes

- Change default shared secret before any real network test
- Store RADIUS secret encrypted at rest in production (currently plain DB field for dev)
- Restrict RADIUS ports to management VLAN only
