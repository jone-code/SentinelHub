import { Table } from 'antd';

const columns = [
  { title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 180 },
  { title: '事件类型', dataIndex: 'event_type', key: 'event_type' },
  { title: '操作者', dataIndex: 'actor_id', key: 'actor_id' },
  { title: '资源', dataIndex: 'resource_id', key: 'resource_id' },
  { title: '结果', dataIndex: 'result', key: 'result', width: 100 },
];

export default function Audit() {
  return (
    <Table
      columns={columns}
      dataSource={[]}
      rowKey="event_id"
      locale={{ emptyText: '暂无审计记录' }}
    />
  );
}
