import { Layout, Menu, Typography } from 'antd';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import Dashboard from './pages/dashboard';
import Devices from './pages/devices';
import Audit from './pages/audit';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/', label: <Link to="/">仪表盘</Link> },
  { key: '/devices', label: <Link to="/devices">设备管控</Link> },
  { key: '/assets', label: <Link to="/assets">资产管理</Link> },
  { key: '/policies', label: <Link to="/policies">策略管理</Link> },
  { key: '/compliance', label: <Link to="/compliance">合规检查</Link> },
  { key: '/dlp', label: <Link to="/dlp">DLP</Link> },
  { key: '/audit', label: <Link to="/audit">审计日志</Link> },
];

export default function App() {
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={220}>
        <div style={{ padding: '16px', textAlign: 'center' }}>
          <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
            SentinelHub
          </Typography.Title>
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} items={menuItems} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px' }}>
          <Typography.Text type="secondary">企业安全办公平台</Typography.Text>
        </Header>
        <Content style={{ margin: 24 }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/devices" element={<Devices />} />
            <Route path="/audit" element={<Audit />} />
            <Route path="*" element={<Placeholder title="模块开发中" />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <div style={{ padding: 48, textAlign: 'center', background: '#fff', borderRadius: 8 }}>
      <Typography.Title level={3}>{title}</Typography.Title>
      <Typography.Text type="secondary">该模块将按路线图分期实现</Typography.Text>
    </div>
  );
}
