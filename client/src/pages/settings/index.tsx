import { Card, Form, Input, Switch, Typography } from 'antd';

export default function Settings() {
  return (
    <div>
      <Typography.Title level={4}>设置</Typography.Title>
      <Card style={{ marginTop: 16 }} title="连接配置">
        <Form layout="vertical" style={{ maxWidth: 480 }}>
          <Form.Item label="服务器地址">
            <Input placeholder="https://api.sentinel.example.com" />
          </Form.Item>
          <Form.Item label="开机自启动" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>
          <Form.Item label="关闭窗口后后台运行" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
