# Grafana OnCall 集成指南

SentinelHub 监控栈默认使用 Prometheus + Alertmanager。如需升级到 Grafana OnCall 统一值班与升级策略，可按以下步骤集成。

## 架构

```
Prometheus → Alertmanager → Grafana OnCall (Webhook) → 钉钉 / 飞书 / PagerDuty / 电话
```

## 快速集成

1. 在 [Grafana Cloud OnCall](https://grafana.com/products/cloud/oncall/) 或自托管 OnCall 创建 Integration（类型选 Alertmanager）。
2. 复制 Integration 的 Webhook URL。
3. 在 `deploy/grafana/alertmanager/alertmanager.yml` 增加 receiver：

```yaml
receivers:
  - name: grafana-oncall
    webhook_configs:
      - url: 'https://oncall-prod-us-central-0.grafana.net/oncall/integrations/v1/alertmanager/XXXX/'
        send_resolved: true
```

4. 将 `route.receiver` 或子路由指向 `grafana-oncall`。

## 自托管 OnCall（可选）

```bash
# 使用 Grafana 官方 Helm Chart 或 docker-compose
# 参考: https://grafana.com/docs/oncall/latest/set-up/
docker compose -f deploy/grafana/docker-compose.oncall.yml up -d
```

## 告警级别路由（当前默认）

| severity | 通道 |
|----------|------|
| critical | PagerDuty |
| warning | 钉钉 |
| info | 飞书 |

OnCall 可在单一入口上配置升级策略，替代多 webhook 直连。

## 环境变量

| 变量 | 说明 |
|------|------|
| `GRAFANA_ONCALL_TOKEN` | OnCall Integration token（可选） |
| `FEISHU_WEBHOOK_URL` | 飞书机器人 Webhook |
| `PAGERDUTY_ROUTING_KEY` | PagerDuty Events API v2 |
