import { Card, Empty, Typography } from 'antd';

export default function Notifications() {
  return (
    <div>
      <Typography.Title level={4}>安全通知</Typography.Title>
      <Card style={{ marginTop: 16 }}>
        <Empty description="暂无安全通知" />
      </Card>
    </div>
  );
}
