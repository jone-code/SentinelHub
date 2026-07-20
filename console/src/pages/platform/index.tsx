import {
  Button,
  Card,
  Col,
  Descriptions,
  message,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiEnvelope } from '../../api/client';

interface TierQuota {
  max_connections: number;
  max_events_per_second: number;
}

interface PlanQuota {
  plan_tier: string;
  plan_quotas_enabled: boolean;
  max_connections: number;
  max_events_per_second: number;
  tier_defaults: TierQuota;
  tier_catalog: Record<string, TierQuota>;
}

interface PlanChangeRequest {
  id: string;
  from_tier: string;
  to_tier: string;
  status: string;
  monthly_price_cents: number;
  currency: string;
  billing_note: string;
  review_note: string | null;
  created_at: string;
}

interface MigrationTable {
  table: string;
  engine_before: string | null;
  status: string;
  step: string;
  message: string;
  rows_copied: number | null;
  total_rows: number | null;
  progress_percent: number | null;
}

interface MigrationStatus {
  status: string;
  message: string;
  tables: MigrationTable[];
}

const tierLabels: Record<string, string> = {
  starter: '入门版',
  business: '商业版',
  enterprise: '企业版',
};

const tierColors: Record<string, string> = {
  starter: 'default',
  business: 'blue',
  enterprise: 'gold',
};

function formatPrice(cents: number, currency: string) {
  return `${(cents / 100).toFixed(2)} ${currency}`;
}

export default function Platform() {
  const [quota, setQuota] = useState<PlanQuota | null>(null);
  const [requests, setRequests] = useState<PlanChangeRequest[]>([]);
  const [migration, setMigration] = useState<MigrationStatus | null>(null);
  const [selectedTier, setSelectedTier] = useState<string>('starter');
  const [submitting, setSubmitting] = useState(false);
  const [migrating, setMigrating] = useState(false);

  const loadQuota = useCallback(() => {
    api.get<ApiEnvelope<PlanQuota>>('/platform/ws-plan-quota')
      .then((res) => {
        setQuota(res.data.data);
        setSelectedTier(res.data.data.plan_tier);
      })
      .catch(() => message.error('加载套餐配额失败'));
  }, []);

  const loadRequests = useCallback(() => {
    api.get<ApiEnvelope<PlanChangeRequest[]>>('/platform/plan-tier/requests')
      .then((res) => setRequests(res.data.data))
      .catch(() => {});
  }, []);

  const loadMigration = useCallback(() => {
    api.get<ApiEnvelope<MigrationStatus>>('/platform/clickhouse-migration')
      .then((res) => setMigration(res.data.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadQuota();
    loadRequests();
    loadMigration();
    const timer = setInterval(loadMigration, 3000);
    return () => clearInterval(timer);
  }, [loadQuota, loadRequests, loadMigration]);

  const submitPlanChange = async () => {
    setSubmitting(true);
    try {
      const res = await api.post<ApiEnvelope<Record<string, unknown>>>('/platform/plan-tier/requests', {
        plan_tier: selectedTier,
      });
      const data = res.data.data;
      if (data.status === 'applied') {
        message.success('套餐已立即生效');
        loadQuota();
      } else {
        message.success('变更申请已提交，等待审批');
      }
      loadRequests();
    } catch {
      message.error('提交变更申请失败');
    } finally {
      setSubmitting(false);
    }
  };

  const approve = async (id: string) => {
    try {
      await api.post(`/platform/plan-tier/requests/${id}/approve`, {});
      message.success('已批准并生效');
      loadQuota();
      loadRequests();
    } catch {
      message.error('批准失败');
    }
  };

  const reject = async (id: string) => {
    try {
      await api.post(`/platform/plan-tier/requests/${id}/reject`, { review_note: 'rejected via console' });
      message.success('已拒绝');
      loadRequests();
    } catch {
      message.error('拒绝失败');
    }
  };

  const runMigration = async () => {
    setMigrating(true);
    try {
      const res = await api.post<ApiEnvelope<MigrationStatus>>('/platform/clickhouse-migration/run');
      setMigration(res.data.data);
      message.success('迁移任务已在后台启动');
    } catch {
      message.error('触发迁移失败');
    } finally {
      setMigrating(false);
    }
  };

  const tierOptions = Object.keys(quota?.tier_catalog ?? {}).map((key) => ({
    value: key,
    label: tierLabels[key] ?? key,
  }));

  const pendingCount = requests.filter((r) => r.status === 'pending').length;

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>平台设置</Typography.Title>
        <Link to="/platform/monitor">
          <Button>打开实时监控 →</Button>
        </Link>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="WebSocket 套餐配额">
            {quota && (
              <Space direction="vertical" style={{ width: '100%' }} size="large">
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="当前套餐">
                    <Tag color={tierColors[quota.plan_tier]}>{tierLabels[quota.plan_tier] ?? quota.plan_tier}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="连接上限">{quota.max_connections}</Descriptions.Item>
                  <Descriptions.Item label="广播速率">{quota.max_events_per_second} /s</Descriptions.Item>
                </Descriptions>

                <div>
                  <Typography.Text type="secondary">申请变更套餐（升级需审批）</Typography.Text>
                  <Select
                    style={{ width: '100%', marginTop: 8 }}
                    value={selectedTier}
                    onChange={setSelectedTier}
                    options={tierOptions}
                  />
                </div>

                <Button
                  type="primary"
                  loading={submitting}
                  onClick={submitPlanChange}
                  disabled={selectedTier === quota.plan_tier}
                >
                  提交变更申请
                </Button>

                <Table
                  size="small"
                  pagination={false}
                  rowKey="tier"
                  dataSource={Object.entries(quota.tier_catalog).map(([tier, q]) => ({ tier, ...q }))}
                  columns={[
                    {
                      title: '套餐',
                      dataIndex: 'tier',
                      render: (tier: string) => <Tag color={tierColors[tier]}>{tierLabels[tier] ?? tier}</Tag>,
                    },
                    { title: '连接数', dataIndex: 'max_connections' },
                    { title: '广播速率', dataIndex: 'max_events_per_second', render: (v: number) => `${v}/s` },
                  ]}
                />
              </Space>
            )}
          </Card>

          <Card title={`变更审批 ${pendingCount > 0 ? `(${pendingCount} 待处理)` : ''}`} style={{ marginTop: 16 }}>
            <Table
              size="small"
              rowKey="id"
              dataSource={requests}
              pagination={{ pageSize: 5 }}
              locale={{ emptyText: '暂无变更记录' }}
              columns={[
                {
                  title: '变更',
                  render: (_, r) => (
                    <span>
                      <Tag>{tierLabels[r.from_tier] ?? r.from_tier}</Tag>
                      →
                      <Tag color={tierColors[r.to_tier]}>{tierLabels[r.to_tier] ?? r.to_tier}</Tag>
                    </span>
                  ),
                },
                {
                  title: '月费',
                  render: (_, r) => formatPrice(r.monthly_price_cents, r.currency),
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  render: (s: string) => (
                    <Tag color={s === 'pending' ? 'processing' : s === 'approved' ? 'success' : 'default'}>{s}</Tag>
                  ),
                },
                {
                  title: '操作',
                  render: (_, r) => r.status === 'pending' ? (
                    <Space>
                      <Button size="small" type="link" onClick={() => approve(r.id)}>批准</Button>
                      <Button size="small" type="link" danger onClick={() => reject(r.id)}>拒绝</Button>
                    </Space>
                  ) : null,
                },
              ]}
            />
          </Card>
        </Col>

        <Col span={12}>
          <Card
            title="ClickHouse 迁移"
            extra={(
              <Button type="primary" loading={migrating} onClick={runMigration}
                disabled={migration?.status === 'running'}>
                后台执行迁移
              </Button>
            )}
          >
            {migration && (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="状态">
                    <Tag color={migration.status === 'running' ? 'processing' : 'default'}>{migration.status}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="说明">{migration.message}</Descriptions.Item>
                </Descriptions>

                {migration.tables.map((t) => (
                  <div key={t.table}>
                    <Typography.Text strong>{t.table}</Typography.Text>
                    <Typography.Text type="secondary" style={{ marginLeft: 8 }}>{t.step}</Typography.Text>
                    {t.progress_percent != null && (
                      <Progress percent={t.progress_percent} size="small"
                        status={t.status === 'failed' ? 'exception' : 'active'} />
                    )}
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
                      {t.message}
                      {t.rows_copied != null && t.total_rows != null ? ` (${t.rows_copied}/${t.total_rows})` : ''}
                    </Typography.Paragraph>
                  </div>
                ))}
              </Space>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
