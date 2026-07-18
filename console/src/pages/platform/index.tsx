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

export default function Platform() {
  const [quota, setQuota] = useState<PlanQuota | null>(null);
  const [migration, setMigration] = useState<MigrationStatus | null>(null);
  const [selectedTier, setSelectedTier] = useState<string>('starter');
  const [saving, setSaving] = useState(false);
  const [migrating, setMigrating] = useState(false);

  const loadQuota = useCallback(() => {
    api.get<ApiEnvelope<PlanQuota>>('/platform/ws-plan-quota')
      .then((res) => {
        setQuota(res.data.data);
        setSelectedTier(res.data.data.plan_tier);
      })
      .catch(() => message.error('加载套餐配额失败'));
  }, []);

  const loadMigration = useCallback(() => {
    api.get<ApiEnvelope<MigrationStatus>>('/platform/clickhouse-migration')
      .then((res) => setMigration(res.data.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadQuota();
    loadMigration();
    const timer = setInterval(loadMigration, 5000);
    return () => clearInterval(timer);
  }, [loadQuota, loadMigration]);

  const savePlanTier = async () => {
    setSaving(true);
    try {
      const res = await api.put<ApiEnvelope<PlanQuota>>('/platform/plan-tier', {
        plan_tier: selectedTier,
      });
      setQuota({ ...quota!, ...res.data.data });
      message.success('套餐已更新');
    } catch {
      message.error('套餐更新失败');
    } finally {
      setSaving(false);
    }
  };

  const runMigration = async () => {
    setMigrating(true);
    try {
      const res = await api.post<ApiEnvelope<MigrationStatus>>('/platform/clickhouse-migration/run');
      setMigration(res.data.data);
      message.success('迁移任务已触发');
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

  return (
    <div>
      <Typography.Title level={4}>平台设置</Typography.Title>

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
                  <Typography.Text type="secondary">升降级套餐</Typography.Text>
                  <Select
                    style={{ width: '100%', marginTop: 8 }}
                    value={selectedTier}
                    onChange={setSelectedTier}
                    options={tierOptions}
                  />
                </div>

                <Button type="primary" loading={saving} onClick={savePlanTier} disabled={selectedTier === quota.plan_tier}>
                  保存套餐
                </Button>

                <Table
                  size="small"
                  pagination={false}
                  rowKey="tier"
                  dataSource={Object.entries(quota.tier_catalog).map(([tier, q]) => ({
                    tier,
                    ...q,
                  }))}
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
        </Col>

        <Col span={12}>
          <Card
            title="ClickHouse 迁移"
            extra={(
              <Button type="primary" loading={migrating} onClick={runMigration}>
                执行迁移
              </Button>
            )}
          >
            {migration && (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="状态">
                    <Tag>{migration.status}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="说明">{migration.message}</Descriptions.Item>
                </Descriptions>

                {migration.tables.map((t) => (
                  <div key={t.table}>
                    <Typography.Text strong>{t.table}</Typography.Text>
                    <Typography.Text type="secondary" style={{ marginLeft: 8 }}>{t.step}</Typography.Text>
                    {t.progress_percent != null && (
                      <Progress percent={t.progress_percent} size="small" status={t.status === 'failed' ? 'exception' : 'active'} />
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
