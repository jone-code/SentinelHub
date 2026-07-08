import { Drawer, Descriptions, Spin, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope } from '../../api/client';

interface DeviceRow {
  id: string;
  agent_id: string;
  hostname: string;
  os_type: string;
  os_version?: string;
  status: string;
  last_seen_at: string;
  compliance_score?: number;
}

interface AssetData {
  hardware: Record<string, unknown> | null;
  software: Array<{ name: string; version: string }>;
}

interface Props {
  deviceId: string | null;
  onClose: () => void;
}

export default function DeviceDetailDrawer({ deviceId, onClose }: Props) {
  const [device, setDevice] = useState<DeviceRow | null>(null);
  const [assets, setAssets] = useState<AssetData | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!deviceId) {
      setDevice(null);
      setAssets(null);
      return;
    }
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<DeviceRow>>(`/devices/${deviceId}`),
      api.get<ApiEnvelope<AssetData>>(`/devices/${deviceId}/assets`),
    ])
      .then(([devRes, assetRes]) => {
        setDevice(devRes.data.data);
        setAssets(assetRes.data.data);
      })
      .catch(() => {
        setDevice(null);
        setAssets(null);
      })
      .finally(() => setLoading(false));
  }, [deviceId]);

  const hw = assets?.hardware;

  return (
    <Drawer title="设备详情" width={560} open={!!deviceId} onClose={onClose}>
      {loading ? (
        <Spin />
      ) : device ? (
        <>
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="主机名">{device.hostname}</Descriptions.Item>
            <Descriptions.Item label="Agent ID">{device.agent_id}</Descriptions.Item>
            <Descriptions.Item label="系统">{device.os_type} {device.os_version}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={device.status === 'online' ? 'green' : 'default'}>{device.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="最后在线">{device.last_seen_at}</Descriptions.Item>
          </Descriptions>

          {hw && (
            <>
              <h4 style={{ marginTop: 24 }}>硬件</h4>
              <Descriptions column={1} bordered size="small">
                <Descriptions.Item label="CPU">{String(hw.cpu_model ?? '—')}</Descriptions.Item>
                <Descriptions.Item label="核心数">{String(hw.cpu_cores ?? '—')}</Descriptions.Item>
                <Descriptions.Item label="内存 (MB)">{String(hw.memory_mb ?? '—')}</Descriptions.Item>
              </Descriptions>
            </>
          )}

          <h4 style={{ marginTop: 24 }}>已安装软件</h4>
          <Table
            size="small"
            rowKey="name"
            pagination={{ pageSize: 10 }}
            dataSource={assets?.software ?? []}
            columns={[
              { title: '名称', dataIndex: 'name' },
              { title: '版本', dataIndex: 'version' },
            ]}
            locale={{ emptyText: '暂无软件清单' }}
          />
        </>
      ) : (
        <p>无法加载设备信息</p>
      )}
    </Drawer>
  );
}
