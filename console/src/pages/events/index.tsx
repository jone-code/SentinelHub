import { Select, Space, Table, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface EventRow {
  id: string;
  event_type: string;
  severity: string;
  hostname: string;
  agent_id: string;
  detail: Record<string, unknown>;
  created_at: string;
}

function formatDetail(row: EventRow) {
  const d = row.detail ?? {};
  const process = d.process as string | undefined;
  const path = d.path as string | undefined;
  const source = d.source as string | undefined;
  if (process) {
    return `进程 ${process}${d.blocked ? ' (已阻断)' : ''}${source ? ` [${source}]` : ''}`;
  }
  if (path) {
    return `路径 ${path}${d.blocked ? ' (已阻断)' : ''}${source ? ` [${source}]` : ''}`;
  }
  return JSON.stringify(d);
}

export default function Events() {
  const [data, setData] = useState<EventRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [eventType, setEventType] = useState<string | undefined>();
  const [severity, setSeverity] = useState<string | undefined>();
  const [storage, setStorage] = useState<'hot' | 'cold'>('hot');

  const load = useCallback(() => {
    setLoading(true);
    api.get<ApiEnvelope<PageData<EventRow>>>('/events', {
      params: {
        page,
        page_size: pageSize,
        event_type: eventType || undefined,
        severity: severity || undefined,
        storage,
      },
    })
      .then((res) => {
        setData(res.data.data.items);
        setTotal(res.data.data.total);
      })
      .catch(() => {
        setData([]);
        setTotal(0);
      })
      .finally(() => setLoading(false));
  }, [page, pageSize, eventType, severity, storage]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 180 },
    { title: '主机', dataIndex: 'hostname', key: 'hostname', width: 140, render: (v: string, row: EventRow) => v || row.agent_id || '—' },
    { title: 'Agent', dataIndex: 'agent_id', key: 'agent_id', width: 120 },
    {
      title: '类型',
      dataIndex: 'event_type',
      key: 'event_type',
      width: 200,
      render: (t: string) => (
        <Tag color={t.startsWith('driver.') ? 'purple' : undefined}>{t}</Tag>
      ),
    },
    {
      title: '级别',
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (s: string) => (
        <Tag color={s === 'critical' ? 'red' : s === 'info' ? 'blue' : 'orange'}>{s}</Tag>
      ),
    },
    {
      title: '详情',
      key: 'detail',
      render: (_: unknown, row: EventRow) => formatDetail(row),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space wrap>
        <Select
          allowClear
          placeholder="事件类型"
          style={{ width: 220 }}
          value={eventType}
          onChange={(v) => {
            setPage(1);
            setEventType(v);
          }}
          options={[
            { value: 'driver.', label: '驱动事件 (driver.*)' },
            { value: 'driver.file_blocked', label: 'driver.file_blocked' },
            { value: 'driver.process_blocked', label: 'driver.process_blocked' },
            { value: 'dlp.', label: 'DLP 事件' },
            { value: 'software.', label: '软件管控' },
          ]}
        />
        <Select
          allowClear
          placeholder="级别"
          style={{ width: 140 }}
          value={severity}
          onChange={(v) => {
            setPage(1);
            setSeverity(v);
          }}
          options={[
            { value: 'critical', label: 'critical' },
            { value: 'warning', label: 'warning' },
            { value: 'info', label: 'info' },
          ]}
        />
        <Select
          style={{ width: 160 }}
          value={storage}
          onChange={(v) => {
            setPage(1);
            setStorage(v);
          }}
          options={[
            { value: 'hot', label: '热存储 (MySQL)' },
            { value: 'cold', label: '冷存储 (ClickHouse)' },
          ]}
        />
      </Space>
      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        locale={{ emptyText: '暂无安全事件' }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />
    </Space>
  );
}
