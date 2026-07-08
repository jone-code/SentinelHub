import { Card, Col, Progress, Row, Statistic, Typography } from 'antd';

export default function Home() {
  return (
    <div>
      <Typography.Title level={4}>安全状态概览</Typography.Title>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={8}>
          <Card>
            <Statistic title="合规评分" value={0} suffix="/ 100" />
            <Progress percent={0} size="small" style={{ marginTop: 12 }} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="待处理项" value={0} suffix="项" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="未读通知" value={0} suffix="条" />
          </Card>
        </Col>
      </Row>
      <Card style={{ marginTop: 16 }} title="连接状态">
        <Typography.Text type="secondary">
          客户端已连接至企业管理平台，后台服务正常运行。
        </Typography.Text>
      </Card>
    </div>
  );
}
