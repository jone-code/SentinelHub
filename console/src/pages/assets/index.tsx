import { Card, Col, Row, Statistic, Table, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface SoftwareRow {
  name: string;
  version: string;
  device_count: number;
  last_collected_at: string;
}

interface HardwareRow {
  device_id: string;
  hostname: string;
  agent_id: string;
  os_type: string;
  os_version: string;
  arch: string;
  cpu_model: string;
  cpu_cores: number;
  memory_mb: number;
  collected_at: string;
}

interface Overview {
  software_entries: number;
  hardware_entries: number;
}

export default function Assets() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [software, setSoftware] = useState<SoftwareRow[]>([]);
  const [hardware, setHardware] = useState<HardwareRow[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<Overview>>('/assets/overview'),
      api.get<ApiEnvelope<PageData<SoftwareRow>>>('/assets/software'),
      api.get<ApiEnvelope<PageData<HardwareRow>>>('/assets/hardware'),
    ])
      .then(([overviewRes, softwareRes, hardwareRes]) => {
        setOverview(overviewRes.data.data);
        setSoftware(softwareRes.data.data.items);
        setHardware(hardwareRes.data.data.items);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={12}>
          <Card loading={loading}>
            <Statistic title="软件条目（去重）" value={overview?.software_entries ?? 0} />
          </Card>
        </Col>
        <Col span={12}>
          <Card loading={loading}>
            <Statistic title="已采集硬件设备" value={overview?.hardware_entries ?? 0} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'software',
              label: '软件清单',
              children: (
                <Table
                  rowKey={(r) => `${r.name}-${r.version}`}
                  loading={loading}
                  dataSource={software}
                  pagination={false}
                  columns={[
                    { title: '软件名称', dataIndex: 'name' },
                    { title: '版本', dataIndex: 'version' },
                    { title: '安装设备数', dataIndex: 'device_count' },
                    {
                      title: '最近采集',
                      dataIndex: 'last_collected_at',
                      render: (v: string) => (v ? new Date(v).toLocaleString() : '—'),
                    },
                  ]}
                />
              ),
            },
            {
              key: 'hardware',
              label: '硬件概览',
              children: (
                <Table
                  rowKey="device_id"
                  loading={loading}
                  dataSource={hardware}
                  pagination={false}
                  columns={[
                    { title: '主机名', dataIndex: 'hostname' },
                    { title: 'Agent ID', dataIndex: 'agent_id' },
                    { title: '系统', render: (_, r) => `${r.os_type} ${r.os_version ?? ''}` },
                    { title: '架构', dataIndex: 'arch' },
                    { title: 'CPU', dataIndex: 'cpu_model' },
                    { title: '核心', dataIndex: 'cpu_cores' },
                    { title: '内存(MB)', dataIndex: 'memory_mb' },
                  ]}
                />
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
