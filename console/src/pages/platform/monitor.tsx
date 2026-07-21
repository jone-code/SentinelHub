import {
  Card, Col, Row, Statistic, Table, Tag, Typography,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import {
  CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { api, ApiEnvelope } from '../../api/client';

interface ChartPoint {
  ts: string;
  websocket_connections: number;
  nats_audit_messages: number;
  nats_client_events_messages: number;
  ws_rejected_global: number;
}

interface PrometheusMetrics {
  chart_point: Record<string, number>;
}

interface NatsStreamMetrics {
  batches_processed: number;
  messages_processed: number;
  batches_failed: number;
  messages_dlq: number;
  backoff_count: number;
}

interface MetricsSummary {
  nats: {
    audit: NatsStreamMetrics;
    client_events: NatsStreamMetrics;
  };
  websocket: {
    total_connections: number;
    tenant_count: number;
    broadcasts_throttled: number;
    global_limit_rejected: number;
    per_tenant: Record<string, number>;
  };
  clickhouse_migration: {
    status: string;
    message: string;
  };
}

const MAX_POINTS = 30;

export default function PlatformMonitor() {
  const [metrics, setMetrics] = useState<MetricsSummary | null>(null);
  const [history, setHistory] = useState<ChartPoint[]>([]);

  const load = useCallback(() => {
    Promise.all([
      api.get<ApiEnvelope<MetricsSummary>>('/platform/metrics-summary'),
      api.get<ApiEnvelope<PrometheusMetrics>>('/platform/prometheus-metrics'),
    ]).then(([summaryRes, promRes]) => {
      setMetrics(summaryRes.data.data);
      const point = promRes.data.data.chart_point;
      const ts = new Date().toLocaleTimeString();
      setHistory((prev) => {
        const next = [...prev, {
          ts,
          websocket_connections: point.websocket_connections ?? 0,
          nats_audit_messages: point.nats_audit_messages ?? 0,
          nats_client_events_messages: point.nats_client_events_messages ?? 0,
          ws_rejected_global: point.ws_rejected_global ?? 0,
        }];
        return next.length > MAX_POINTS ? next.slice(next.length - MAX_POINTS) : next;
      });
    }).catch(() => {});
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [load]);

  const ws = metrics?.websocket;
  const nats = metrics?.nats;
  const perTenantRows = ws
    ? Object.entries(ws.per_tenant ?? {}).map(([tenant, count]) => ({ tenant, count }))
    : [];

  return (
    <div>
      <Typography.Title level={4}>平台监控</Typography.Title>
      <Typography.Paragraph type="secondary">
        Prometheus 指标趋势 + NATS / WebSocket 实时数据（3 秒刷新，保留最近 {MAX_POINTS} 个采样点）
      </Typography.Paragraph>

      <Card title="Prometheus 指标趋势" style={{ marginBottom: 16 }}>
        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={history}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="ts" hide />
            <YAxis />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="websocket_connections" name="WS 连接" stroke="#1677ff" dot={false} />
            <Line type="monotone" dataKey="nats_audit_messages" name="NATS Audit" stroke="#52c41a" dot={false} />
            <Line type="monotone" dataKey="nats_client_events_messages" name="NATS Events" stroke="#faad14" dot={false} />
            <Line type="monotone" dataKey="ws_rejected_global" name="全局拒绝" stroke="#ff4d4f" dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </Card>

      <Row gutter={16}>
        <Col span={6}>
          <Card><Statistic title="WebSocket 总连接" value={ws?.total_connections ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="活跃租户" value={ws?.tenant_count ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="全局池拒绝" value={ws?.global_limit_rejected ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title="广播限流" value={ws?.broadcasts_throttled ?? 0} /></Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="NATS Audit 消费">
            <Row gutter={16}>
              <Col span={8}><Statistic title="批处理" value={nats?.audit.batches_processed ?? 0} /></Col>
              <Col span={8}><Statistic title="消息数" value={nats?.audit.messages_processed ?? 0} /></Col>
              <Col span={8}><Statistic title="失败批" value={nats?.audit.batches_failed ?? 0} /></Col>
            </Row>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="NATS Client Events 消费">
            <Row gutter={16}>
              <Col span={8}><Statistic title="批处理" value={nats?.client_events.batches_processed ?? 0} /></Col>
              <Col span={8}><Statistic title="消息数" value={nats?.client_events.messages_processed ?? 0} /></Col>
              <Col span={8}><Statistic title="失败批" value={nats?.client_events.batches_failed ?? 0} /></Col>
            </Row>
          </Card>
        </Col>
      </Row>

      <Card
        title="WebSocket 租户连接分布"
        style={{ marginTop: 16 }}
        extra={metrics?.clickhouse_migration && (
          <Tag color={metrics.clickhouse_migration.status === 'running' ? 'processing' : 'default'}>
            CH 迁移: {metrics.clickhouse_migration.status}
          </Tag>
        )}
      >
        <Table
          size="small"
          pagination={false}
          rowKey="tenant"
          dataSource={perTenantRows}
          locale={{ emptyText: '暂无活跃连接' }}
          columns={[
            { title: '租户 ID', dataIndex: 'tenant', ellipsis: true },
            { title: '连接数', dataIndex: 'count', width: 100 },
          ]}
        />
      </Card>
    </div>
  );
}
