import { Button, Card, Form, Input, Select, Switch, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface MdmProfile {
  id: string;
  name: string;
  profile_type: string;
  content: Record<string, unknown>;
  enabled: boolean;
}

interface Assignment {
  device_id: string;
  profile_id: string;
  profile_name: string;
  profile_type: string;
  hostname: string;
  agent_id: string;
  status: string;
  assigned_at: string;
}

export default function Mdm() {
  const [profiles, setProfiles] = useState<MdmProfile[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [profileForm] = Form.useForm();
  const [assignForm] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<MdmProfile[]>>('/mdm/profiles'),
      api.get<ApiEnvelope<PageData<Assignment>>>('/mdm/assignments'),
    ])
      .then(([profilesRes, assignmentsRes]) => {
        setProfiles(profilesRes.data.data ?? []);
        setAssignments(assignmentsRes.data.data.items);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const createProfile = async () => {
    const values = await profileForm.validateFields();
    let content: Record<string, unknown> = {};
    try {
      content = JSON.parse(values.content_json);
    } catch {
      message.error('配置内容必须是合法 JSON');
      return;
    }
    await api.post('/mdm/profiles', {
      name: values.name,
      profile_type: values.profile_type,
      content,
      enabled: values.enabled ?? true,
    });
    message.success('配置描述文件已创建');
    profileForm.resetFields();
    load();
  };

  const assignProfile = async () => {
    const values = await assignForm.validateFields();
    await api.post('/mdm/assignments', values);
    message.success('已分配配置');
    assignForm.resetFields();
    load();
  };

  return (
    <div>
      <Card title="新建配置描述文件" loading={loading} style={{ marginBottom: 24 }}>
        <Form form={profileForm} layout="vertical" onFinish={createProfile} initialValues={{ profile_type: 'wifi', enabled: true }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="企业 Wi-Fi" />
          </Form.Item>
          <Form.Item name="profile_type" label="类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'wifi', label: 'Wi-Fi' },
                { value: 'restriction', label: '限制策略' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="content_json"
            label="配置 JSON"
            rules={[{ required: true }]}
            initialValue='{"ssid":"Corp-WiFi","security":"WPA2","auto_join":true}'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            创建
          </Button>
        </Form>
      </Card>

      <Card title="配置列表" loading={loading} style={{ marginBottom: 24 }}>
        <Table
          rowKey="id"
          dataSource={profiles}
          pagination={false}
          columns={[
            { title: '名称', dataIndex: 'name' },
            { title: '类型', dataIndex: 'profile_type' },
            {
              title: '状态',
              dataIndex: 'enabled',
              render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>),
            },
          ]}
        />
      </Card>

      <Card title="分配配置" loading={loading} style={{ marginBottom: 24 }}>
        <Form form={assignForm} layout="inline" onFinish={assignProfile}>
          <Form.Item name="device_id" rules={[{ required: true }]}>
            <Input placeholder="设备 ID" style={{ width: 280 }} />
          </Form.Item>
          <Form.Item name="profile_id" rules={[{ required: true }]}>
            <Select
              placeholder="选择配置"
              style={{ width: 200 }}
              options={profiles.map((p) => ({ value: p.id, label: p.name }))}
            />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            分配
          </Button>
        </Form>
      </Card>

      <Card title="分配记录" loading={loading}>
        <Table
          rowKey={(r) => `${r.device_id}-${r.profile_id}`}
          dataSource={assignments}
          pagination={false}
          columns={[
            { title: '主机名', dataIndex: 'hostname' },
            { title: '配置', dataIndex: 'profile_name' },
            { title: '类型', dataIndex: 'profile_type' },
            {
              title: '状态',
              dataIndex: 'status',
              render: (v: string) => (
                <Tag color={v === 'applied' ? 'green' : 'orange'}>{v}</Tag>
              ),
            },
            {
              title: '分配时间',
              dataIndex: 'assigned_at',
              render: (v: string) => new Date(v).toLocaleString(),
            },
          ]}
        />
      </Card>
    </div>
  );
}
