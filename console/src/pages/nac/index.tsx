import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Switch, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface NacRadius {
  enabled: boolean;
  server_host: string;
  auth_port: number;
  acct_port: number;
  secret_masked?: string;
  nas_identifier?: string;
  vlan_allowed?: string;
  vlan_restricted?: string;
  vlan_denied?: string;
  updated_at?: string;
}

interface NacPolicy {
  id: string;
  name: string;
  min_compliance_score: number;
  action_on_fail: string;
  isolate_vlan?: string;
  enabled: boolean;
  updated_at: string;
}

interface DeviceStatus {
  device_id: string;
  hostname: string;
  agent_id: string;
  access_state: string;
  compliance_score: number;
  evaluated_at: string;
}

const stateColors: Record<string, string> = {
  allowed: 'green',
  restricted: 'orange',
  denied: 'red',
  unknown: 'default',
};

export default function Nac() {
  const [policy, setPolicy] = useState<NacPolicy | null>(null);
  const [devices, setDevices] = useState<DeviceStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [radiusSaving, setRadiusSaving] = useState(false);
  const [form] = Form.useForm();
  const [radiusForm] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<NacPolicy>>('/nac/policy'),
      api.get<ApiEnvelope<PageData<DeviceStatus>>>('/nac/devices'),
      api.get<ApiEnvelope<NacRadius>>('/nac/radius'),
    ])
      .then(([policyRes, devicesRes, radiusRes]) => {
        const p = policyRes.data.data;
        if (p?.id) {
          setPolicy(p);
          form.setFieldsValue({
            name: p.name,
            min_compliance_score: p.min_compliance_score,
            action_on_fail: p.action_on_fail,
            isolate_vlan: p.isolate_vlan,
            enabled: p.enabled,
          });
        }
        setDevices(devicesRes.data.data.items);
        const r = radiusRes.data.data;
        if (r?.server_host) {
          radiusForm.setFieldsValue({
            enabled: r.enabled,
            server_host: r.server_host,
            auth_port: r.auth_port,
            acct_port: r.acct_port,
            nas_identifier: r.nas_identifier,
            vlan_allowed: r.vlan_allowed,
            vlan_restricted: r.vlan_restricted,
            vlan_denied: r.vlan_denied,
          });
        }
      })
      .catch(() => message.error('加载 NAC 数据失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const res = await api.put<ApiEnvelope<NacPolicy>>('/nac/policy', values);
      setPolicy(res.data.data);
      message.success('准入策略已保存');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const saveRadius = async () => {
    const values = await radiusForm.validateFields();
    setRadiusSaving(true);
    try {
      await api.put('/nac/radius', values);
      message.success('RADIUS 配置已保存');
      load();
    } catch {
      message.error('保存失败');
    } finally {
      setRadiusSaving(false);
    }
  };

  const columns = [
    { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
    { title: 'Agent ID', dataIndex: 'agent_id', key: 'agent_id' },
    {
      title: '准入状态',
      dataIndex: 'access_state',
      key: 'access_state',
      render: (s: string) => <Tag color={stateColors[s] ?? 'default'}>{s}</Tag>,
    },
    {
      title: '合规分',
      dataIndex: 'compliance_score',
      key: 'compliance_score',
      render: (s: number) => <Tag color={s >= 80 ? 'green' : 'red'}>{s}</Tag>,
    },
    { title: '评估时间', dataIndex: 'evaluated_at', key: 'evaluated_at' },
  ];

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <div style={{ color: '#888' }}>当前策略</div>
            <div style={{ fontSize: 18, fontWeight: 500 }}>{policy?.name ?? '未配置'}</div>
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <div style={{ color: '#888' }}>最低合规分</div>
            <div style={{ fontSize: 18, fontWeight: 500 }}>{policy?.min_compliance_score ?? '-'} 分</div>
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <div style={{ color: '#888' }}>已评估设备</div>
            <div style={{ fontSize: 18, fontWeight: 500 }}>{devices.length} 台</div>
          </Card>
        </Col>
      </Row>

      <Card title="准入策略配置" style={{ marginBottom: 24 }}>
        {policy ? (
          <Form form={form} layout="vertical">
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="name" label="策略名称" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="min_compliance_score" label="最低合规分" rules={[{ required: true }]}>
                  <InputNumber min={0} max={100} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="action_on_fail" label="不合规时动作" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'restrict', label: '限制访问 (restrict)' },
                    { value: 'deny', label: '拒绝入网 (deny)' },
                    { value: 'allow', label: '仅告警 (allow)' },
                  ]} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="isolate_vlan" label="隔离 VLAN">
                  <Input placeholder="quarantine" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="enabled" label="启用策略" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Button type="primary" loading={saving} onClick={save}>保存策略</Button>
            <span style={{ marginLeft: 16, color: '#888' }}>更新于 {policy.updated_at}</span>
          </Form>
        ) : (
          <div style={{ color: '#888' }}>暂无准入策略，请检查种子数据是否已初始化</div>
        )}
      </Card>

      <Card title="RADIUS 集成（测试环境模板）" style={{ marginBottom: 24 }}>
        <Form form={radiusForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="server_host" label="RADIUS 服务器">
                <Input placeholder="radius.example.local" />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="auth_port" label="认证端口">
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="acct_port" label="计费端口">
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="secret" label="共享密钥（留空则不修改）">
                <Input.Password placeholder="changeme" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="nas_identifier" label="NAS 标识">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="enabled" label="启用 RADIUS" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}><Form.Item name="vlan_allowed" label="允许 VLAN"><Input /></Form.Item></Col>
            <Col span={8}><Form.Item name="vlan_restricted" label="限制 VLAN"><Input /></Form.Item></Col>
            <Col span={8}><Form.Item name="vlan_denied" label="拒绝 VLAN"><Input /></Form.Item></Col>
          </Row>
          <Button type="primary" loading={radiusSaving} onClick={saveRadius}>保存 RADIUS</Button>
        </Form>
      </Card>

      <Card title="设备准入状态">
        <Table
          columns={columns}
          dataSource={devices}
          rowKey="device_id"
          loading={loading}
          locale={{ emptyText: '暂无设备准入评估记录，客户端上报后显示' }}
        />
      </Card>
    </div>
  );
}
