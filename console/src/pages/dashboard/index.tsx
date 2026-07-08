import { Card, Col, Row, Statistic } from 'antd';

export default function Dashboard() {
  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="纳管设备" value={0} suffix="台" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="在线设备" value={0} suffix="台" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="合规率" value={0} suffix="%" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="今日告警" value={0} suffix="条" />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
