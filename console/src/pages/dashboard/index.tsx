import { Card, Col, List, Row, Statistic, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiEnvelope } from '../../api/client';

interface Insight {
  id: string;
  title: string;
  severity: string;
  insight_type: string;
}

interface Summary {
  device_total: number;
  device_online: number;
  alert_open: number;
  compliance_avg: number;
  trust_avg: number;
  ai_open: number;
  remote_active: number;
  recent_insights: Insight[];
}

const severityColors: Record<string, string> = {
  high: 'red',
  warning: 'orange',
  info: 'blue',
};

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary>({
    device_total: 0,
    device_online: 0,
    alert_open: 0,
    compliance_avg: 0,
    trust_avg: 0,
    ai_open: 0,
    remote_active: 0,
    recent_insights: [],
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
            <Statistic title="平均信任分" value={summary.trust_avg} suffix="/100" />
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={6}>
          <Card>
            <Statistic title="今日告警" value={summary.alert_open} suffix="条" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="待处理洞察" value={summary.ai_open} suffix="条" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="远程会话" value={summary.remote_active} suffix="个" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Link to="/ai">运行 AI 分析 →</Link>
          </Card>
        </Col>
      </Row>

      {summary.recent_insights.length > 0 && (
        <Card title="待处理安全洞察" style={{ marginTop: 16 }}>
          <List
            dataSource={summary.recent_insights}
            renderItem={(item) => (
              <List.Item>
                <Tag color={severityColors[item.severity] ?? 'default'}>{item.severity}</Tag>
                {item.title}
              </List.Item>
            )}
          />
        </Card>
      )}
    </div>
  );
}
