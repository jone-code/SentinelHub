import { Card, List, Tag, Typography } from 'antd';

const items = [
  { title: '操作系统补丁', status: 'pending', desc: '待检查' },
  { title: '杀毒软件', status: 'pending', desc: '待检查' },
  { title: '防火墙', status: 'pending', desc: '待检查' },
  { title: '磁盘加密', status: 'pending', desc: '待检查' },
];

export default function Compliance() {
  return (
    <div>
      <Typography.Title level={4}>合规状态</Typography.Title>
      <Card style={{ marginTop: 16 }}>
        <List
          dataSource={items}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta title={item.title} description={item.desc} />
              <Tag color="default">待扫描</Tag>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
