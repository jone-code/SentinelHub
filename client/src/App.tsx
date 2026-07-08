import { Layout, Menu, Typography, Tag } from 'antd';
import {
  HomeOutlined,
  SafetyOutlined,
  DesktopOutlined,
  BellOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import Home from './pages/home';
import Compliance from './pages/compliance';
import Device from './pages/device';
import Notifications from './pages/notifications';
import Settings from './pages/settings';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/', icon: <HomeOutlined />, label: <Link to="/">首页</Link> },
  { key: '/compliance', icon: <SafetyOutlined />, label: <Link to="/compliance">合规状态</Link> },
  { key: '/device', icon: <DesktopOutlined />, label: <Link to="/device">本机信息</Link> },
  { key: '/notifications', icon: <BellOutlined />, label: <Link to="/notifications">安全通知</Link> },
  { key: '/settings', icon: <SettingOutlined />, label: <Link to="/settings">设置</Link> },
];

export default function App() {
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={200} style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '20px 16px' }}>
          <Typography.Title level={5} style={{ margin: 0 }}>
            SentinelHub
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            安全客户端
          </Typography.Text>
        </div>
        <Menu mode="inline" selectedKeys={[location.pathname]} items={menuItems} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px', borderBottom: '1px solid #f0f0f0' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography.Text>企业安全办公客户端</Typography.Text>
            <Tag color="green">服务运行中</Tag>
          </div>
        </Header>
        <Content style={{ margin: 24 }}>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/compliance" element={<Compliance />} />
            <Route path="/device" element={<Device />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}
