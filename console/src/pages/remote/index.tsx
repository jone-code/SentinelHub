import { Button, Card, Form, Input, Modal, Select, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';
import { useRemoteWebRtc } from './webrtc';

interface Device {
  id: string;
  hostname: string;
  agent_id: string;
  status: string;
}

interface RemoteSession {
  id: string;
  device_id: string;
  hostname: string;
  agent_id: string;
  operator_name: string;
  status: string;
  reason: string;
  consent_required: boolean;
  started_at: string | null;
  ended_at: string | null;
  recording_key: string | null;
  created_at: string;
}

const statusColors: Record<string, string> = {
  pending: 'orange',
  active: 'green',
  ended: 'default',
  cancelled: 'red',
};

export default function Remote() {
  const [sessions, setSessions] = useState<RemoteSession[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [signalSession, setSignalSession] = useState<RemoteSession | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [form] = Form.useForm();

  const { connect, cleanup, videoRef, connectionState } = useRemoteWebRtc(signalSession?.id ?? null);

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<PageData<RemoteSession>>>('/remote/sessions'),
      api.get<ApiEnvelope<PageData<Device>>>('/devices', { params: { page_size: 100 } }),
    ])
      .then(([sessionsRes, devicesRes]) => {
        setSessions(sessionsRes.data.data.items);
        setDevices(devicesRes.data.data.items);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    return () => cleanup();
  }, [cleanup]);

  const createSession = async () => {
    const values = await form.validateFields();
    setCreating(true);
    try {
      await api.post('/remote/sessions', values);
      message.success('远程协助请求已发起');
      form.resetFields();
      load();
    } finally {
      setCreating(false);
    }
  };

  const endSession = async (id: string) => {
    await api.post(`/remote/sessions/${id}/end`, {});
    message.success('会话已结束');
    load();
  };

  const cancelSession = async (id: string) => {
    await api.post(`/remote/sessions/${id}/cancel`);
    message.success('会话已取消');
    load();
  };

  const downloadRecording = async (id: string) => {
    const res = await api.get<ApiEnvelope<{ presigned_url?: string; available: boolean }>>(
      `/remote/sessions/${id}/recording-url`,
    );
    const url = res.data.data.presigned_url;
    if (url) {
      window.open(url, '_blank');
    } else {
      message.info('录像暂不可用或未上传');
    }
  };

  const openWebRtc = (session: RemoteSession) => {
    setSignalSession(session);
  };

  const startConnection = async () => {
    setConnecting(true);
    try {
      await connect();
      message.success('WebRTC 连接已发起，等待客户端应答…');
    } catch (err) {
      message.error('WebRTC 连接失败');
    } finally {
      setConnecting(false);
    }
  };

  const closeModal = () => {
    cleanup();
    setSignalSession(null);
  };

  return (
    <div>
      <Card title="发起远程协助" style={{ marginBottom: 24 }}>
        <Form form={form} layout="inline" onFinish={createSession} initialValues={{ consent_required: true }}>
          <Form.Item name="device_id" rules={[{ required: true, message: '请选择设备' }]}>
            <Select
              placeholder="选择目标设备"
              style={{ width: 280 }}
              options={devices.map((d) => ({
                value: d.id,
                label: `${d.hostname} (${d.agent_id})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="reason">
            <Input placeholder="协助原因（可选）" style={{ width: 240 }} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={creating}>
            发起请求
          </Button>
        </Form>
      </Card>

      <Card title="会话记录" loading={loading}>
        <Table
          rowKey="id"
          dataSource={sessions}
          pagination={false}
          columns={[
            { title: '主机名', dataIndex: 'hostname' },
            { title: '操作员', dataIndex: 'operator_name' },
            { title: '原因', dataIndex: 'reason', render: (v: string) => v || '—' },
            {
              title: '状态',
              dataIndex: 'status',
              render: (v: string) => <Tag color={statusColors[v] ?? 'default'}>{v}</Tag>,
            },
            {
              title: '创建时间',
              dataIndex: 'created_at',
              render: (v: string) => new Date(v).toLocaleString(),
            },
            {
              title: '操作',
              render: (_, row) => (
                <>
                  {row.status === 'active' && (
                    <Button type="link" onClick={() => openWebRtc(row)}>
                      连接画面
                    </Button>
                  )}
                  {row.recording_key && (
                    <Button type="link" onClick={() => downloadRecording(row.id)}>
                      录像
                    </Button>
                  )}
                  {(row.status === 'pending' || row.status === 'active') && (
                    <>
                      <Button type="link" onClick={() => endSession(row.id)}>
                        结束
                      </Button>
                      <Button type="link" danger onClick={() => cancelSession(row.id)}>
                        取消
                      </Button>
                    </>
                  )}
                </>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title={`远程画面 — ${signalSession?.hostname ?? ''}`}
        open={Boolean(signalSession)}
        onCancel={closeModal}
        footer={[
          <Button key="connect" type="primary" loading={connecting} onClick={startConnection}>
            建立 WebRTC 连接
          </Button>,
          <Button key="close" onClick={closeModal}>
            关闭
          </Button>,
        ]}
        width={800}
      >
        <p style={{ color: '#666' }}>
          连接状态：<Tag>{connectionState}</Tag>
        </p>
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          style={{ width: '100%', maxHeight: 400, background: '#000', borderRadius: 8 }}
        />
      </Modal>
    </div>
  );
}
