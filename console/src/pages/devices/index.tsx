import { Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface DeviceRow {
  id: string;
  hostname: string;
  os_type: string;
  status: string;
  last_seen_at: string;
}

export default function Devices() {
  const [data, setData] = useState<DeviceRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<ApiEnvelope<PageData<DeviceRow>>>('/devices')
      .then((res) => setData(res.data.data.items))
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
    { title: '操作系统', dataIndex: 'os_type', key: 'os_type' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => (
        <Tag color={s === 'online' ? 'green' : 'default'}>{s}</Tag>
      ),
    },
    { title: '最后在线', dataIndex: 'last_seen_at', key: 'last_seen_at' },
  ];

  return (
    <Table
      columns={columns}
      dataSource={data}
      rowKey="id"
      loading={loading}
      locale={{ emptyText: '暂无设备，请部署客户端后自动纳管' }}
    />
  );
}
