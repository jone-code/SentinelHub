import { Select, Space, Table, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface TimelineRow {
  id: string;
  source: 'audit' | 'client_event';
  created_at: string;
  title: string;
  actor_type: string;
  actor_id: string;
  resource: string;
  severity: string | null;
  detail: Record<string, unknown> | string;
}

function formatDetail(detail: TimelineRow['detail']) {
  if (!detail) return '';
  if (typeof detail === 'string') {
    try {
      const parsed = JSON.parse(detail);
      return JSON.stringify(parsed);
    } catch {
      return detail;
    }
  }
  return JSON.stringify(detail);
}

export default function Timeline() {
  const [data, setData] = useState<TimelineRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [source, setSource] = useState<string | undefined>();

  const load = useCallback(() => {
    setLoading(true);
    api.get<ApiEnvelope<PageData<TimelineRow>>>('/timeline', {
      params: {
        page,
        page_size: pageSize,
        source: source || undefined,
        storage: 'cold',
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
  }, [page, pageSize, source]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 180 },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (s: string) => (
        <Tag color={s === 'audit' ? 'blue' : 'purple'}>
          {s === 'audit' ? '审计日志' : '客户端事件'}
        </Tag>
      ),
    },
    { title: '标题', dataIndex: 'title', key: 'title', width: 220 },
    { title: '操作者类型', dataIndex: 'actor_type', key: 'actor_type', width: 100 },
    { title: '操作者/设备', dataIndex: 'actor_id', key: 'actor_id', width: 140 },
    {
      title: '级别',
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (s: string | null) => s ? (
        <Tag color={s === 'critical' ? 'red' : s === 'info' ? 'blue' : 'orange'}>{s}</Tag>
      ) : '—',
    },
    {
      title: '详情',
      key: 'detail',
      ellipsis: true,
      render: (_: unknown, row: TimelineRow) => formatDetail(row.detail),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space wrap>
        <Select
          allowClear
          placeholder="来源筛选"
          style={{ width: 180 }}
          value={source}
          onChange={(v) => {
            setPage(1);
            setSource(v);
          }}
          options={[
            { value: 'audit', label: '审计日志' },
            { value: 'client_event', label: '客户端事件' },
          ]}
        />
      </Space>
      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        locale={{ emptyText: '暂无冷存储时间线记录（需启用 ClickHouse）' }}
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
