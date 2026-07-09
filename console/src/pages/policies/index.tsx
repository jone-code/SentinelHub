import { Button, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface PolicyRow {
  id: string;
  name: string;
  type: string;
  status: string;
  priority: number;
  scope?: { mode?: string; ids?: string[] };
  updated_at: string;
}

interface ScopeOption {
  id: string;
  name: string;
}

const scopeModeLabels: Record<string, string> = {
  all: '全部设备',
  org_unit: '组织单元',
  device_group: '设备组',
};

export default function Policies() {
  const [data, setData] = useState<PolicyRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [orgUnits, setOrgUnits] = useState<ScopeOption[]>([]);
  const [deviceGroups, setDeviceGroups] = useState<ScopeOption[]>([]);
  const [form] = Form.useForm();
  const scopeMode = Form.useWatch('scope_mode', form);

  const load = () => {
    setLoading(true);
    api.get<ApiEnvelope<PolicyRow[]>>('/policies')
      .then((res) => setData(res.data.data))
      .catch(() => message.error('加载策略失败'))
      .finally(() => setLoading(false));
  };

  const loadScopeOptions = () => {
    Promise.all([
      api.get<ApiEnvelope<ScopeOption[]>>('/org-units'),
      api.get<ApiEnvelope<ScopeOption[]>>('/device-groups'),
    ]).then(([orgRes, groupRes]) => {
      setOrgUnits(orgRes.data.data);
      setDeviceGroups(groupRes.data.data);
    }).catch(() => {});
  };

  useEffect(() => {
    load();
    loadScopeOptions();
  }, []);

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
    const scope = values.scope_mode === 'all'
      ? { mode: 'all' }
      : { mode: values.scope_mode, ids: values.scope_ids ?? [] };
    try {
      await api.post('/policies', {
        name: values.name,
        type: values.type,
        priority: values.priority,
        content,
        scope,
      });
      message.success('策略已创建（草稿）');
      setModalOpen(false);
      form.resetFields();
      load();
    } catch {
      message.error('创建失败');
    }
  };

  const renderScope = (scope?: PolicyRow['scope']) => {
    const mode = scope?.mode ?? 'all';
    const label = scopeModeLabels[mode] ?? mode;
    const count = scope?.ids?.length ?? 0;
    if (mode === 'all' || count === 0) {
      return <Tag>{label}</Tag>;
    }
    return <Tag color="blue">{label} ({count})</Tag>;
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '类型', dataIndex: 'type', key: 'type' },
    {
      title: '作用域',
      dataIndex: 'scope',
      key: 'scope',
      render: (scope: PolicyRow['scope']) => renderScope(scope),
    },
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

  const scopeOptions = scopeMode === 'org_unit' ? orgUnits : deviceGroups;

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
          scope_mode: 'all',
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
          <Form.Item name="scope_mode" label="作用域">
            <Select options={[
              { value: 'all', label: '全部设备' },
              { value: 'org_unit', label: '组织单元' },
              { value: 'device_group', label: '设备组' },
            ]} />
          </Form.Item>
          {scopeMode && scopeMode !== 'all' && (
            <Form.Item name="scope_ids" label="目标" rules={[{ required: true, message: '请选择作用域目标' }]}>
              <Select
                mode="multiple"
                options={scopeOptions.map((o) => ({ value: o.id, label: o.name }))}
                placeholder="选择组织或设备组"
              />
            </Form.Item>
          )}
          <Form.Item name="content_json" label="策略内容 (JSON)" rules={[{ required: true }]}>
            <Input.TextArea rows={10} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
