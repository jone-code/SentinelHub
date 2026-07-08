import {
  Button, Form, Input, Modal, Select, Switch, Table, Tabs, Tag, message,
} from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface DlpRule {
  id: string;
  name: string;
  channel: string;
  action: string;
  patterns: string[];
  enabled: boolean;
  priority: number;
  updated_at: string;
}

interface DlpEvent {
  id: string;
  event_type: string;
  severity: string;
  hostname: string;
  detail: Record<string, unknown>;
  created_at: string;
}

interface EvidenceRow {
  id: string;
  filename: string;
  size_bytes: number;
  sha256: string;
  hostname: string;
  channel?: string;
  created_at: string;
}

const channelOptions = [
  { value: 'usb', label: 'USB 外设' },
  { value: 'sensitive_path', label: '敏感文件路径' },
];

export default function Dlp() {
  const [rules, setRules] = useState<DlpRule[]>([]);
  const [events, setEvents] = useState<DlpEvent[]>([]);
  const [evidence, setEvidence] = useState<EvidenceRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpRule | null>(null);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<DlpRule[]>>('/dlp/rules'),
      api.get<ApiEnvelope<PageData<DlpEvent>>>('/dlp/events'),
      api.get<ApiEnvelope<PageData<EvidenceRow>>>('/dlp/evidence'),
    ])
      .then(([rulesRes, eventsRes, evidenceRes]) => {
        setRules(rulesRes.data.data);
        setEvents(eventsRes.data.data.items);
        setEvidence(evidenceRes.data.data.items);
      })
      .catch(() => message.error('加载 DLP 数据失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      channel: 'usb',
      action: 'alert',
      enabled: true,
      priority: 100,
      patterns: 'removable',
    });
    setModalOpen(true);
  };

  const openEdit = (rule: DlpRule) => {
    setEditing(rule);
    form.setFieldsValue({
      name: rule.name,
      channel: rule.channel,
      action: rule.action,
      enabled: rule.enabled,
      priority: rule.priority,
      patterns: rule.patterns.join('\n'),
    });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    const patterns = String(values.patterns).split('\n').map((s: string) => s.trim()).filter(Boolean);
    const payload = {
      name: values.name,
      channel: values.channel,
      action: values.action,
      enabled: values.enabled,
      priority: values.priority,
      patterns,
    };
    try {
      if (editing) {
        await api.put(`/dlp/rules/${editing.id}`, payload);
        message.success('规则已更新');
      } else {
        await api.post('/dlp/rules', payload);
        message.success('规则已创建');
      }
      setModalOpen(false);
      load();
    } catch {
      message.error('保存失败');
    }
  };

  const ruleColumns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '通道', dataIndex: 'channel', key: 'channel' },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      render: (a: string) => <Tag color={a === 'block' ? 'red' : 'orange'}>{a}</Tag>,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (e: boolean) => <Tag color={e ? 'green' : 'default'}>{e ? '是' : '否'}</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: DlpRule) => (
        <Button type="link" onClick={() => openEdit(row)}>编辑</Button>
      ),
    },
  ];

  const downloadEvidence = async (id: string) => {
    try {
      const res = await api.get<ApiEnvelope<{ download_url?: string; message?: string }>>(
        `/dlp/evidence/${id}/download`,
      );
      const url = res.data.data.download_url;
      if (url) {
        window.open(url, '_blank');
      } else {
        message.warning(res.data.data.message ?? 'MinIO 未启用，无法下载');
      }
    } catch {
      message.error('获取下载链接失败');
    }
  };

  const evidenceColumns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 200 },
    { title: '主机', dataIndex: 'hostname', key: 'hostname' },
    { title: '文件名', dataIndex: 'filename', key: 'filename' },
    { title: '大小', dataIndex: 'size_bytes', key: 'size_bytes', render: (n: number) => `${n} B` },
    { title: '通道', dataIndex: 'channel', key: 'channel' },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, row: EvidenceRow) => (
        <Button type="link" onClick={() => downloadEvidence(row.id)}>下载</Button>
      ),
    },
  ];

  const eventColumns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 200 },
    { title: '主机', dataIndex: 'hostname', key: 'hostname' },
    { title: '类型', dataIndex: 'event_type', key: 'event_type' },
    {
      title: '级别',
      dataIndex: 'severity',
      key: 'severity',
      render: (s: string) => <Tag color={s === 'critical' ? 'red' : 'orange'}>{s}</Tag>,
    },
    {
      title: '详情',
      key: 'detail',
      render: (_: unknown, row: DlpEvent) => {
        const d = row.detail ?? {};
        return String(d.detail ?? d.rule_name ?? JSON.stringify(d));
      },
    },
  ];

  return (
    <div>
      <Tabs
        items={[
          {
            key: 'rules',
            label: 'DLP 规则',
            children: (
              <>
                <div style={{ marginBottom: 16 }}>
                  <Button type="primary" onClick={openCreate}>新建规则</Button>
                </div>
                <Table columns={ruleColumns} dataSource={rules} rowKey="id" loading={loading} />
              </>
            ),
          },
          {
            key: 'events',
            label: 'DLP 事件',
            children: (
              <Table
                columns={eventColumns}
                dataSource={events}
                rowKey="id"
                loading={loading}
                locale={{ emptyText: '暂无 DLP 事件' }}
              />
            ),
          },
          {
            key: 'evidence',
            label: '取证文件',
            children: (
              <Table
                columns={evidenceColumns}
                dataSource={evidence}
                rowKey="id"
                loading={loading}
                locale={{ emptyText: '暂无取证文件（敏感文件违规时自动上传至 MinIO）' }}
              />
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑 DLP 规则' : '新建 DLP 规则'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="channel" label="通道" rules={[{ required: true }]}>
            <Select options={channelOptions} />
          </Form.Item>
          <Form.Item name="action" label="动作" rules={[{ required: true }]}>
            <Select options={[
              { value: 'alert', label: '告警' },
              { value: 'block', label: '阻断' },
            ]} />
          </Form.Item>
          <Form.Item name="patterns" label="匹配模式（每行一个）" rules={[{ required: true }]}>
            <Input.TextArea rows={4} placeholder="usb: removable&#10;sensitive_path: *.pem" />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
