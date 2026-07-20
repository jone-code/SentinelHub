import {
  Card, Col, Row, Statistic, Table, Tag, Typography,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

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
    max_connections_global: number;
    per_tenant: Record<string, number>;
  };
  clickhouse_migration: {
    status: string;
    message: string;
  };
}

export default function PlatformMonitor() {
  const [metrics, setMetrics] = useState<MetricsSummary | null>(null);

  const load = useCallback(() => {
    api.get<ApiEnvelope<MetricsSummary>>('/platform/metrics-summary')
      .then((res) => setMetrics(res.data.data))
      .catch(() => {});
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
      <Typography.Paragraph type="secondary">NATS 消费与 WebSocket 连接实时指标（3 秒刷新）</Typography.Paragraph>

      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="WebSocket 总连接" value={ws?.total_connections ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="活跃租户" value={ws?.tenant_count ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="全局池拒绝" value={ws?.global_limit_rejected ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="广播限流" value={ws?.broadcasts_throttled ?? 0} />
          </Card>
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
            <Row gutter={16} style={{ marginTop: 16 }}>
              <Col span={12}><Statistic title="DLQ" value={nats?.audit.messages_dlq ?? 0} /></Col>
              <Col span={12}><Statistic title="退避" value={nats?.audit.backoff_count ?? 0} /></Col>
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
            <Row gutter={16} style={{ marginTop: 16 }}>
              <Col span={12}><Statistic title="DLQ" value={nats?.client_events.messages_dlq ?? 0} /></Col>
              <Col span={12}><Statistic title="退避" value={nats?.client_events.backoff_count ?? 0} /></Col>
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
