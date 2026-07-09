import { Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
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

export default function Events() {
  const [data, setData] = useState<EventRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<ApiEnvelope<PageData<EventRow>>>('/events')
      .then((res) => setData(res.data.data.items))
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 200 },
    { title: '主机', dataIndex: 'hostname', key: 'hostname' },
    {
      title: '类型',
      dataIndex: 'event_type',
      key: 'event_type',
      render: (t: string) => <Tag>{t}</Tag>,
    },
    {
      title: '级别',
      dataIndex: 'severity',
      key: 'severity',
      render: (s: string) => (
        <Tag color={s === 'critical' ? 'red' : 'orange'}>{s}</Tag>
      ),
    },
    {
      title: '详情',
      key: 'detail',
      render: (_: unknown, row: EventRow) => {
        const d = row.detail ?? {};
        const process = d.process as string | undefined;
        const rule = d.matched_rule as string | undefined;
        if (process) return `进程 ${process} 命中规则 ${rule ?? ''}`;
        return JSON.stringify(d);
      },
    },
  ];

  return (
    <Table
      columns={columns}
      dataSource={data}
      rowKey="id"
      loading={loading}
      locale={{ emptyText: '暂无安全事件' }}
    />
  );
}
