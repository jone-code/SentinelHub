import { Button, Card, Col, Form, Input, InputNumber, Row, Switch, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface ZtPolicy {
  id: string;
  name: string;
  compliance_weight: number;
  nac_weight: number;
  event_weight: number;
  min_trust_score: number;
  enabled: boolean;
}

interface ProtectedApp {
  id: string;
  name: string;
  app_identifier: string;
  min_trust_score: number;
  enabled: boolean;
}

interface DeviceTrust {
  device_id: string;
  hostname: string;
  agent_id: string;
  compliance_score: number;
  trust_score: number;
  trust_level: string;
}

const levelColors: Record<string, string> = {
  high: 'green',
  medium: 'orange',
  low: 'red',
};

export default function Zerotrust() {
  const [apps, setApps] = useState<ProtectedApp[]>([]);
  const [devices, setDevices] = useState<DeviceTrust[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [appForm] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<ZtPolicy>>('/zerotrust/policy'),
      api.get<ApiEnvelope<ProtectedApp[]>>('/zerotrust/protected-apps'),
      api.get<ApiEnvelope<PageData<DeviceTrust>>>('/zerotrust/devices'),
    ])
      .then(([policyRes, appsRes, devicesRes]) => {
        const p = policyRes.data.data;
        if (p?.id) {
          form.setFieldsValue(p);
        }
        setApps(appsRes.data.data ?? []);
        setDevices(devicesRes.data.data.items);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const savePolicy = async () => {
    const values = await form.validateFields();
    const total = values.compliance_weight + values.nac_weight + values.event_weight;
    if (total !== 100) {
      message.error('权重之和必须为 100');
      return;
    }
    setSaving(true);
    try {
      await api.put('/zerotrust/policy', values);
      message.success('策略已保存');
      load();
    } finally {
      setSaving(false);
    }
  };

  const createApp = async () => {
    const values = await appForm.validateFields();
    await api.post('/zerotrust/protected-apps', values);
    message.success('受保护应用已添加');
    appForm.resetFields();
    load();
  };

  return (
    <div>
      <Card title="信任分策略" loading={loading} style={{ marginBottom: 24 }}>
        <Form form={form} layout="vertical" onFinish={savePolicy}>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="name" label="策略名称" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="compliance_weight" label="合规权重" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="nac_weight" label="准入权重" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="event_weight" label="事件权重" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="min_trust_score" label="最低信任分" rules={[{ required: true }]}>
                <InputNumber min={0} max={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={saving}>
            保存策略
          </Button>
        </Form>
      </Card>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="受保护应用" loading={loading}>
            <Form form={appForm} layout="inline" style={{ marginBottom: 16 }} onFinish={createApp}>
              <Form.Item name="name" rules={[{ required: true }]}>
                <Input placeholder="应用名称" />
              </Form.Item>
              <Form.Item name="app_identifier" rules={[{ required: true }]}>
                <Input placeholder="Bundle ID / 路径" />
              </Form.Item>
              <Form.Item name="min_trust_score" initialValue={70}>
                <InputNumber min={0} max={100} placeholder="最低分" />
              </Form.Item>
              <Form.Item name="enabled" valuePropName="checked" initialValue={true}>
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
              <Button type="primary" htmlType="submit">
                添加
              </Button>
            </Form>
            <Table
              rowKey="id"
              dataSource={apps}
              pagination={false}
              size="small"
              columns={[
                { title: '名称', dataIndex: 'name' },
                { title: '标识', dataIndex: 'app_identifier' },
                { title: '最低分', dataIndex: 'min_trust_score' },
                {
                  title: '状态',
                  dataIndex: 'enabled',
                  render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>),
                },
              ]}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="设备信任分" loading={loading}>
            <Table
              rowKey="device_id"
              dataSource={devices}
              pagination={false}
              size="small"
              columns={[
                { title: '主机名', dataIndex: 'hostname' },
                { title: '合规', dataIndex: 'compliance_score' },
                { title: '信任分', dataIndex: 'trust_score' },
                {
                  title: '等级',
                  dataIndex: 'trust_level',
                  render: (v: string) => <Tag color={levelColors[v] ?? 'default'}>{v}</Tag>,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
