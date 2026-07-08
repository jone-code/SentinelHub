# SentinelHub PC 安全客户端

安装在员工 PC（Windows / macOS / Linux）上的**桌面客户端**，包含用户界面和后台服务两部分。

> 技术栈详见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 架构

```
client/
├── electron/           # Electron 主进程（窗口、托盘、启动后台服务）
├── src/                # React UI 页面
│   └── pages/
│       ├── home/           # 安全状态概览
│       ├── compliance/     # 合规状态
│       ├── device/         # 本机信息
│       ├── notifications/  # 安全通知
│       └── settings/       # 设置
└── service/            # Go 后台服务（心跳、策略执行、数据采集）
    ├── cmd/service/
    ├── core/
    ├── collectors/
    └── enforcers/
```

| 组件 | 技术 | 说明 |
|------|------|------|
| 桌面 UI | Electron + React + TypeScript + Ant Design | 用户可见页面 |
| 后台服务 | Go 1.22+ | 无界面，负责心跳、策略、采集 |
| UI API | `/api/client/v1` | 合规状态、通知、本机信息 |
| 服务 API | `/api/client/v1/service` | 注册、心跳、资产/事件上报 |

## 页面功能

| 页面 | 功能 |
|------|------|
| 首页 | 合规评分、待处理项、连接状态 |
| 合规状态 | 基线检查项及通过/失败 |
| 本机信息 | 主机名、OS、客户端版本 |
| 安全通知 | DLP 告警、策略变更通知 |
| 设置 | 服务器地址、自启动、后台运行 |

## 开发

```bash
# UI 开发
cd client
npm install
npm run dev

# 后台服务
cd client/service
CLIENT_SERVER_URL=http://localhost:8080 go run ./cmd/service
```

## 构建

```bash
npm run build          # 构建 UI
go build -o sentinel-service ./service/cmd/service   # 构建后台服务
# 打包为 MSI/PKG/DEB 时由 Electron Builder 捆绑 UI + service
```

## 支持平台

| 平台 | 安装包 |
|------|--------|
| Windows 10/11 | `.msi` |
| macOS 12+ | `.pkg` |
| Linux | `.deb` / `.rpm` |
