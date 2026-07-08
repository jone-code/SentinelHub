import { Card, Descriptions, Typography } from 'antd';

export default function Device() {
  return (
    <div>
      <Typography.Title level={4}>本机信息</Typography.Title>
      <Card style={{ marginTop: 16 }}>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="主机名">—</Descriptions.Item>
          <Descriptions.Item label="操作系统">—</Descriptions.Item>
          <Descriptions.Item label="客户端版本">0.1.0-dev</Descriptions.Item>
          <Descriptions.Item label="客户端 ID">—</Descriptions.Item>
          <Descriptions.Item label="最近同步">—</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
}
