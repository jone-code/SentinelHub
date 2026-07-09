import {
  Button, Card, Col, Form, Input, InputNumber, Row, Statistic, Switch, Table, Tabs, Tag, message,
} from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface Overview {
  device_scanned: number;
  average_score: number;
}

interface ResultRow {
  id: string;
  hostname: string;
  agent_id: string;
  score: number;
  passed: number;
  failed: number;
  scanned_at: string;
}

interface BaselineRule {
  id: string;
  name: string;
  weight: number;
  enabled: boolean;
}

interface Baseline {
  id: string;
  name: string;
  framework?: string;
  rules: BaselineRule[];
  updated_at: string;
}

const RULE_DESCRIPTIONS: Record<string, string> = {
  firewall: '检查系统防火墙是否启用',
  os_updates: '检查是否有待重启的系统更新',
  disk_encryption: '检查磁盘是否加密（Linux: LUKS）',
  antivirus: '检查是否安装杀毒软件（Linux: ClamAV）',
};

export default function Compliance() {
  const [overview, setOverview] = useState<Overview>({ device_scanned: 0, average_score: 0 });
  const [results, setResults] = useState<ResultRow[]>([]);
  const [baseline, setBaseline] = useState<Baseline | null>(null);
  const [rules, setRules] = useState<BaselineRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const loadResults = () => Promise.all([
    api.get<ApiEnvelope<Overview>>('/compliance/overview'),
    api.get<ApiEnvelope<PageData<ResultRow>>>('/compliance/results'),
  ]).then(([ov, res]) => {
    setOverview(ov.data.data);
    setResults(res.data.data.items);
  });

  const loadBaseline = () => api.get<ApiEnvelope<Baseline[]>>('/compliance/baselines')
    .then((res) => {
      const active = res.data.data[0];
      if (!active) return;
      setBaseline(active);
      setRules(active.rules.map((r) => ({ ...r, enabled: r.enabled !== false })));
      form.setFieldsValue({ name: active.name });
    });

  const load = () => {
    setLoading(true);
    Promise.all([loadResults(), loadBaseline()])
      .catch(() => message.error('加载合规数据失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const saveBaseline = async () => {
    if (!baseline) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      const res = await api.put<ApiEnvelope<Baseline>>(`/compliance/baselines/${baseline.id}`, {
        name: values.name,
        rules,
      });
      setBaseline(res.data.data);
      setRules(res.data.data.rules);
      message.success('基线已保存，客户端将在下次心跳时同步');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const updateRule = (id: string, patch: Partial<BaselineRule>) => {
    setRules((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
  };

  const resultColumns = [
    { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
    {
      title: '合规分',
      dataIndex: 'score',
      key: 'score',
      render: (s: number) => (
        <Tag color={s >= 80 ? 'green' : s >= 60 ? 'orange' : 'red'}>{s}</Tag>
      ),
    },
    { title: '通过', dataIndex: 'passed', key: 'passed' },
    { title: '未通过', dataIndex: 'failed', key: 'failed' },
    { title: '扫描时间', dataIndex: 'scanned_at', key: 'scanned_at' },
  ];

  const ruleColumns = [
    { title: '检查项 ID', dataIndex: 'id', key: 'id', width: 140 },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row: BaselineRule) => (
        <Input
          value={name}
          onChange={(e) => updateRule(row.id, { name: e.target.value })}
          disabled={!row.enabled}
        />
      ),
    },
    {
      title: '说明',
      key: 'desc',
      render: (_: unknown, row: BaselineRule) => RULE_DESCRIPTIONS[row.id] ?? '-',
    },
    {
      title: '权重',
      dataIndex: 'weight',
      key: 'weight',
      width: 100,
      render: (weight: number, row: BaselineRule) => (
        <InputNumber
          min={1}
          max={100}
          value={weight}
          disabled={!row.enabled}
          onChange={(v) => updateRule(row.id, { weight: v ?? 1 })}
        />
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean, row: BaselineRule) => (
        <Switch checked={enabled} onChange={(v) => updateRule(row.id, { enabled: v })} />
      ),
    },
  ];

  const overviewCards = (
    <Row gutter={16} style={{ marginBottom: 24 }}>
      <Col span={8}>
        <Card>
          <Statistic title="已扫描设备" value={overview.device_scanned} suffix="台" />
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic title="平均合规分" value={overview.average_score} suffix="分" />
        </Card>
      </Col>
      <Col span={8}>
        <Card>
          <Statistic
            title="基线规则"
            value={rules.filter((r) => r.enabled).length}
            suffix={`/ ${rules.length} 项启用`}
          />
        </Card>
      </Col>
    </Row>
  );

  return (
    <div>
      {overviewCards}
      <Tabs
        items={[
          {
            key: 'results',
            label: '扫描结果',
            children: (
              <Table
                columns={resultColumns}
                dataSource={results}
                rowKey="id"
                loading={loading}
                locale={{ emptyText: '暂无合规扫描结果，请部署客户端后自动扫描' }}
              />
            ),
          },
          {
            key: 'baseline',
            label: '基线配置',
            children: baseline ? (
              <div>
                <Form form={form} layout="inline" style={{ marginBottom: 16 }}>
                  <Form.Item name="name" label="基线名称" rules={[{ required: true }]}>
                    <Input style={{ width: 240 }} />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" loading={saving} onClick={saveBaseline}>保存基线</Button>
                  </Form.Item>
                  <Form.Item>
                    <span style={{ color: '#888' }}>最近更新: {baseline.updated_at}</span>
                  </Form.Item>
                </Form>
                <Table
                  columns={ruleColumns}
                  dataSource={rules}
                  rowKey="id"
                  pagination={false}
                  loading={loading}
                />
              </div>
            ) : (
              <div style={{ padding: 24, textAlign: 'center', color: '#888' }}>暂无合规基线</div>
            ),
          },
        ]}
      />
    </div>
  );
}
