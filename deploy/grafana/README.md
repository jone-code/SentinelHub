# Grafana 监控栈

本地开发用 Prometheus + Alertmanager + Grafana，抓取 `sentinelhub-server` 的 `/actuator/prometheus` 指标，并将告警路由至 PagerDuty / 钉钉。

## 启动

```bash
# 先启动 API（默认 8080）
make backend-run

# 配置告警凭据（可选）
cp deploy/grafana/.env.example deploy/grafana/.env
# 编辑 .env 填入 PAGERDUTY_ROUTING_KEY、DINGTALK_WEBHOOK_URL

# 再启动监控栈
cd deploy/grafana && docker compose --env-file .env up -d
```

- Grafana: http://localhost:3001 （admin / admin）
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093

## 告警路由

| 严重级别 | 接收方 |
|----------|--------|
| `critical` | PagerDuty（`PAGERDUTY_ROUTING_KEY`） |
| `warning` | 钉钉（经 `prometheus-webhook-dingtalk` 转发） |

## 内容

| 路径 | 说明 |
|------|------|
| `dashboards/sentinelhub-platform.json` | NATS + WebSocket 平台仪表盘 |
| `alerts/sentinelhub-alerts.yml` | Prometheus 告警规则 |
| `alertmanager/alertmanager.yml` | PagerDuty + 钉钉路由 |
| `provisioning/` | Grafana 数据源与仪表盘自动加载 |
| `prometheus.yml` | 抓取 `host.docker.internal:8080` |

## 指标

- `sentinel.websocket.*` — 连接数、租户数、全局拒绝、广播限流
- `sentinel.nats.audit.*` / `sentinel.nats.client_events.*` — 批处理、DLQ、退避

Prometheus 导出时 `.` 会转为 `_`，计数器带 `_total` 后缀。
