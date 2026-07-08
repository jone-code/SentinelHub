import { Button, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface PolicyRow {
  id: string;
  name: string;
  type: string;
  status: string;
  priority: number;
  updated_at: string;
}

export default function Policies() {
  const [data, setData] = useState<PolicyRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    api.get<ApiEnvelope<PolicyRow[]>>('/policies')
      .then((res) => setData(res.data.data))
      .catch(() => message.error('加载策略失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const publish = async (id: string) => {
    try {
      await api.post(`/policies/${id}/publish`);
      message.success('策略已发布，客户端将在下次心跳时拉取');
      load();
    } catch {
      message.error('发布失败');
    }
  };

  const create = async () => {
    const values = await form.validateFields();
    let content: Record<string, unknown>;
    try {
      content = JSON.parse(values.content_json);
    } catch {
      message.error('策略内容必须是合法 JSON');
      return;
    }
    try {
      await api.post('/policies', {
        name: values.name,
        type: values.type,
        priority: values.priority,
        content,
      });
      message.success('策略已创建（草稿）');
      setModalOpen(false);
      form.resetFields();
      load();
    } catch {
      message.error('创建失败');
    }
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '类型', dataIndex: 'type', key: 'type' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => (
        <Tag color={s === 'published' ? 'green' : 'default'}>{s}</Tag>
      ),
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '更新时间', dataIndex: 'updated_at', key: 'updated_at' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: PolicyRow) => (
        <Space>
          {row.status === 'draft' && (
            <Button type="link" onClick={() => publish(row.id)}>发布</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setModalOpen(true)}>新建策略</Button>
      </div>
      <Table columns={columns} dataSource={data} rowKey="id" loading={loading} />

      <Modal
        title="新建策略"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={create}
        width={640}
      >
        <Form form={form} layout="vertical" initialValues={{
          type: 'software',
          priority: 100,
          content_json: JSON.stringify({
            blacklist: ['utorrent.exe'],
            whitelist: [],
            action: 'alert',
          }, null, 2),
        }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={[
              { value: 'software', label: '软件管控' },
              { value: 'compliance', label: '合规检查' },
            ]} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="content_json" label="策略内容 (JSON)" rules={[{ required: true }]}>
            <Input.TextArea rows={10} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
