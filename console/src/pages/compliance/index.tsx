import { Card, Col, Row, Statistic, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface Overview {
  device_scanned: number;
  average_score: number;
}

interface ResultRow {
  id: string;
  hostname: string;
  agent_id: string;
  score: number;
  passed: number;
  failed: number;
  scanned_at: string;
}

export default function Compliance() {
  const [overview, setOverview] = useState<Overview>({ device_scanned: 0, average_score: 0 });
  const [results, setResults] = useState<ResultRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api.get<ApiEnvelope<Overview>>('/compliance/overview'),
      api.get<ApiEnvelope<PageData<ResultRow>>>('/compliance/results'),
    ])
      .then(([ov, res]) => {
        setOverview(ov.data.data);
        setResults(res.data.data.items);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: '主机名', dataIndex: 'hostname', key: 'hostname' },
    {
      title: '合规分',
      dataIndex: 'score',
      key: 'score',
      render: (s: number) => (
        <Tag color={s >= 80 ? 'green' : s >= 60 ? 'orange' : 'red'}>{s}</Tag>
      ),
    },
    { title: '通过', dataIndex: 'passed', key: 'passed' },
    { title: '未通过', dataIndex: 'failed', key: 'failed' },
    { title: '扫描时间', dataIndex: 'scanned_at', key: 'scanned_at' },
  ];

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic title="已扫描设备" value={overview.device_scanned} suffix="台" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="平均合规分" value={overview.average_score} suffix="分" />
          </Card>
        </Col>
      </Row>
      <Table
        columns={columns}
        dataSource={results}
        rowKey="id"
        loading={loading}
        locale={{ emptyText: '暂无合规扫描结果，请部署客户端后自动扫描' }}
      />
    </div>
  );
}
