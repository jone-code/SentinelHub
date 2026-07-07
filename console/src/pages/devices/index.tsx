import { Table, Tag } from 'antd';

const columns = [
  { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
  { title: '操作系统', dataIndex: 'os_type', key: 'os_type' },
  { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
  { title: '最后在线', dataIndex: 'last_seen_at', key: 'last_seen_at' },
];

export default function Devices() {
  return (
    <Table
      columns={columns}
      dataSource={[]}
      rowKey="id"
      locale={{ emptyText: '暂无设备，请部署 Agent 后自动纳管' }}
    />
  );
}
