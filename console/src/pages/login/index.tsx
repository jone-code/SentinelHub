import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiEnvelope, setToken } from '../../api/client';

interface LoginData {
  access_token: string;
  user: { email: string; name: string };
}

export default function Login() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);
    try {
      const res = await api.post<ApiEnvelope<LoginData>>('/auth/login', {
        email: values.email,
        password: values.password,
        tenant_slug: 'demo',
      });
      setToken(res.data.data.access_token);
      message.success(`欢迎，${res.data.data.user.name}`);
      navigate('/');
    } catch {
      message.error('登录失败，请检查邮箱和密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card style={{ width: 400 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>SentinelHub</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          企业安全办公平台
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish} initialValues={{ email: 'admin@demo.local', password: 'admin123' }}>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
