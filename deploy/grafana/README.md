# Grafana 监控栈

本地开发用 Prometheus + Grafana，抓取 `sentinelhub-server` 的 `/actuator/prometheus` 指标。

## 启动

```bash
# 先启动 API（默认 8080）
make backend-run

# 再启动监控栈
cd deploy/grafana && docker compose up -d
```

- Grafana: http://localhost:3001 （admin / admin）
- Prometheus: http://localhost:9090

## 内容

| 路径 | 说明 |
|------|------|
| `dashboards/sentinelhub-platform.json` | NATS + WebSocket 平台仪表盘 |
| `alerts/sentinelhub-alerts.yml` | Prometheus 告警规则 |
| `provisioning/` | Grafana 数据源与仪表盘自动加载 |
| `prometheus.yml` | 抓取 `host.docker.internal:8080` |

## 指标

- `sentinel.websocket.*` — 连接数、租户数、全局拒绝、广播限流
- `sentinel.nats.audit.*` / `sentinel.nats.client_events.*` — 批处理、DLQ、退避

Prometheus 导出时 `.` 会转为 `_`，计数器带 `_total` 后缀。
