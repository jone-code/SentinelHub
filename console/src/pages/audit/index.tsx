import { Table } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface AuditRow {
  id: string;
  created_at: string;
  action: string;
  actor_id: string;
  resource_id: string;
}

export default function Audit() {
  const [data, setData] = useState<AuditRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<ApiEnvelope<PageData<AuditRow>>>('/audit/logs')
      .then((res) => setData(res.data.data.items))
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', width: 200 },
    { title: '动作', dataIndex: 'action', key: 'action' },
    { title: '操作者', dataIndex: 'actor_id', key: 'actor_id' },
    { title: '资源 ID', dataIndex: 'resource_id', key: 'resource_id' },
  ];

  return (
    <Table
      columns={columns}
      dataSource={data}
      rowKey="id"
      loading={loading}
      locale={{ emptyText: '暂无审计记录' }}
    />
  );
}
