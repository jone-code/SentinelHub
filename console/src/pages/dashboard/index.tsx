import { Card, Col, Row, Statistic } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface Summary {
  device_total: number;
  device_online: number;
  alert_open: number;
  compliance_avg: number;
}

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary>({
    device_total: 0,
    device_online: 0,
    alert_open: 0,
    compliance_avg: 0,
  });

  useEffect(() => {
    api.get<ApiEnvelope<Summary>>('/dashboard/summary')
      .then((res) => setSummary(res.data.data))
      .catch(() => {});
  }, []);

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="纳管设备" value={summary.device_total} suffix="台" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="在线设备" value={summary.device_online} suffix="台" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="合规率" value={summary.compliance_avg} suffix="%" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="今日告警" value={summary.alert_open} suffix="条" />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
