import { Select, Space, Table } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface AuditRow {
  id: string;
  created_at: string;
  action: string;
  actor_type: string;
  actor_id: string;
  resource: string;
  resource_id: string;
  detail: Record<string, unknown> | string;
  ip_address: string;
}

function formatDetail(detail: AuditRow['detail']) {
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

export default function Audit() {
  const [data, setData] = useState<AuditRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [action, setAction] = useState<string | undefined>();
  const [storage, setStorage] = useState<'hot' | 'cold'>('hot');

  const load = useCallback(() => {
    setLoading(true);
    api.get<ApiEnvelope<PageData<AuditRow>>>('/audit/logs', {
      params: {
        page,
        page_size: pageSize,
        action: action || undefined,
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
  }, [page, pageSize, action, storage]);

  useEffect(() => {
    load();
  }, [load]);

  const columns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 180 },
    { title: '动作', dataIndex: 'action', key: 'action', width: 200 },
    { title: '操作者类型', dataIndex: 'actor_type', key: 'actor_type', width: 100 },
    { title: '操作者', dataIndex: 'actor_id', key: 'actor_id', width: 140 },
    { title: '资源', dataIndex: 'resource', key: 'resource', width: 100 },
    { title: '资源 ID', dataIndex: 'resource_id', key: 'resource_id', width: 120 },
    {
      title: '详情',
      key: 'detail',
      ellipsis: true,
      render: (_: unknown, row: AuditRow) => formatDetail(row.detail),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space wrap>
        <Select
          allowClear
          placeholder="动作筛选 (如 driver.)"
          style={{ width: 220 }}
          value={action}
          onChange={(v) => {
            setPage(1);
            setAction(v);
          }}
          options={[
            { value: 'driver.', label: '驱动事件 (driver.*)' },
            { value: 'dlp.', label: 'DLP' },
            { value: 'software.', label: '软件管控' },
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
        locale={{ emptyText: '暂无审计记录' }}
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
