import { Button, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface DeviceGroup {
  id: string;
  name: string;
  description?: string;
  member_count: number;
  created_at: string;
}

interface DeviceRow {
  id: string;
  hostname: string;
  agent_id: string;
}

export default function DeviceGroups() {
  const [groups, setGroups] = useState<DeviceGroup[]>([]);
  const [devices, setDevices] = useState<DeviceRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [memberOpen, setMemberOpen] = useState(false);
  const [activeGroup, setActiveGroup] = useState<DeviceGroup | null>(null);
  const [form] = Form.useForm();
  const [memberForm] = Form.useForm();

  const load = () => {
    setLoading(true);
    api.get<ApiEnvelope<DeviceGroup[]>>('/device-groups')
      .then((res) => setGroups(res.data.data))
      .catch(() => message.error('加载设备组失败'))
      .finally(() => setLoading(false));
  };

  const loadDevices = () => {
    api.get<ApiEnvelope<{ items: DeviceRow[] }>>('/devices?page=1&page_size=200')
      .then((res) => setDevices(res.data.data.items ?? []))
      .catch(() => {});
  };

  useEffect(() => {
    load();
    loadDevices();
  }, []);

  const createGroup = async () => {
    const values = await form.validateFields();
    try {
      await api.post('/device-groups', values);
      message.success('设备组已创建');
      setCreateOpen(false);
      form.resetFields();
      load();
    } catch {
      message.error('创建失败');
    }
  };

  const openMembers = (group: DeviceGroup) => {
    setActiveGroup(group);
    memberForm.resetFields();
    setMemberOpen(true);
  };

  const addMembers = async () => {
    if (!activeGroup) return;
    const values = await memberForm.validateFields();
    try {
      await api.post(`/device-groups/${activeGroup.id}/members`, {
        device_ids: values.device_ids,
      });
      message.success('成员已添加');
      setMemberOpen(false);
      load();
    } catch {
      message.error('添加失败');
    }
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    {
      title: '成员数',
      dataIndex: 'member_count',
      key: 'member_count',
      render: (n: number) => <Tag color="blue">{n}</Tag>,
    },
    { title: '创建时间', dataIndex: 'created_at', key: 'created_at' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: DeviceGroup) => (
        <Button type="link" onClick={() => openMembers(row)}>管理成员</Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Button type="primary" onClick={() => setCreateOpen(true)}>新建设备组</Button>
        </Space>
      </div>
      <Table columns={columns} dataSource={groups} rowKey="id" loading={loading} />

      <Modal title="新建设备组" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={createGroup}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={activeGroup ? `添加成员 — ${activeGroup.name}` : '添加成员'}
        open={memberOpen}
        onCancel={() => setMemberOpen(false)}
        onOk={addMembers}
      >
        <Form form={memberForm} layout="vertical">
          <Form.Item name="device_ids" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select
              mode="multiple"
              options={devices.map((d) => ({
                value: d.id,
                label: `${d.hostname ?? d.agent_id} (${d.agent_id})`,
              }))}
              placeholder="选择要加入的设备"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
