# 客户端技术栈

SentinelHub 有三类客户端，分别对接统一 API 服务的不同通道。

```
                    ┌─────────────────────────────────┐
                    │     sentinel-server (:8080)      │
                    └───────────┬─────────────────────┘
            ┌───────────────────┼───────────────────┐
            │                   │                   │
   /api/admin/v1        /api/app/v1        /api/client/v1
            │                   │                   │
    ┌───────▼───────┐   ┌───────▼───────┐   ┌───────▼───────┐
    │  管理控制台     │   │  手机管理 App  │   │  PC 安全客户端  │
    │  console/      │   │  mobile/       │   │  client/       │
    │  PC 浏览器      │   │  iOS/Android   │   │  桌面应用+页面  │
    └───────────────┘   └───────────────┘   └───────────────┘
```

---

## 1. 三端总览

| 客户端 | 目录 | 运行环境 | API 通道 | 阶段 |
|--------|------|----------|----------|------|
| 管理控制台 | `console/` | PC 浏览器 | `/api/admin/v1` | P0 |
| 手机管理 App | `mobile/` | iOS / Android | `/api/app/v1` | P0（API）/ P1（App） |
| **PC 安全客户端** | `client/` | Windows / macOS / Linux 桌面应用 | `/api/client/v1` | P0 |

---

## 2. 管理控制台（PC Web）— `console/`

面向安全管理员、IT 运维，浏览器访问，完整管理能力。

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| 语言 | TypeScript | 5.x |
| 构建 | Vite | 5.x |
| UI | Ant Design | 5.x |
| 路由 | React Router | 6.x |
| HTTP（规划） | Axios + TanStack Query | — |

```bash
cd console && npm install && npm run dev   # http://localhost:3000
```

---

## 3. 手机管理 App — `mobile/`

面向管理员移动办公，设备概览与告警处置。

| 类别 | 技术 |
|------|------|
| 框架 | React Native + Expo |
| 语言 | TypeScript |
| UI | React Native Paper |
| API | `/api/app/v1` |

**状态**：规划阶段（P1 开发）。

---

## 4. PC 安全客户端 — `client/`

安装在员工电脑上的**桌面应用程序**，包含 **用户界面（页面）** 和 **后台服务** 两部分，不是无界面的纯 Agent。

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────┐
│              SentinelHub PC 客户端 (client/)          │
│  ┌─────────────────────┐  ┌────────────────────────┐ │
│  │   Electron 主进程    │  │   Go 后台服务 (service/) │ │
│  │   窗口 / 托盘 / 启动  │──│   心跳 / 策略 / 采集    │ │
│  └──────────┬──────────┘  └───────────┬────────────┘ │
│             │                          │               │
│  ┌──────────▼──────────┐               │               │
│  │  React UI（渲染进程） │               │               │
│  │  首页 / 合规 / 设置   │               │               │
│  └─────────────────────┘               │               │
└─────────────────────────┬──────────────┴───────────────┘
                          │ HTTPS
              ┌───────────▼───────────┐
              │  /api/client/v1        │  ← UI 页面数据
              │  /api/client/v1/service │  ← 后台服务通信
              └───────────────────────┘
```

### 4.2 UI 层技术选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 桌面壳 | **Electron** | 33.x | 跨平台桌面窗口、系统托盘 |
| 框架 | React | 18.x | 与控制台技术栈一致 |
| 语言 | TypeScript | 5.x | 类型安全 |
| 构建 | Vite | 5.x | UI 构建 |
| UI 组件 | Ant Design | 5.x | 与控制台风格统一 |
| 路由 | React Router | 6.x | 页面导航 |
| 打包（规划） | electron-builder | — | MSI / PKG / DEB |

### 4.3 后台服务技术选型

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Go 1.22+ | 低资源占用，适合常驻后台 |
| 通信 | HTTPS + mTLS | 对接 `/api/client/v1/service` |
| 协议 | JSON / Protobuf | `proto/agent/`（服务协议） |
| 本地存储 | BoltDB / SQLite（规划） | 策略缓存、离线队列 |
| 运行方式 | 系统服务 + Electron 拉起 | 关闭窗口后仍可后台运行 |

### 4.4 客户端页面

| 页面 | 路径 | 功能 | 对接 API |
|------|------|------|----------|
| 首页 | `/` | 合规评分、待处理项、连接状态 | `GET /api/client/v1/status` |
| 合规状态 | `/compliance` | 基线检查项明细 | `GET /api/client/v1/compliance` |
| 本机信息 | `/device` | 主机名、OS、版本 | `GET /api/client/v1/device`（规划） |
| 安全通知 | `/notifications` | DLP 告警、策略通知 | `GET /api/client/v1/notifications`（规划） |
| 设置 | `/settings` | 服务器地址、自启动 | 本地配置 |

### 4.5 目录结构

```
client/
├── package.json
├── electron/
│   ├── main.js             # 主进程：窗口、托盘、启动 Go 服务
│   └── preload.js
├── src/                    # React UI
│   ├── App.tsx
│   └── pages/
│       ├── home/
│       ├── compliance/
│       ├── device/
│       ├── notifications/
│       └── settings/
└── service/                # Go 后台服务
    ├── cmd/service/
    ├── core/
    ├── transport/
    ├── collectors/
    ├── enforcers/
    └── platform/
```

### 4.6 API 对接

**UI 接口**（`/api/client/v1`）— 供桌面页面展示：

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/client/v1/status` | 安全状态概览 |
| GET | `/api/client/v1/compliance` | 合规检查明细 |
| GET | `/api/client/v1/notifications` | 通知列表（规划） |

**服务接口**（`/api/client/v1/service`）— 供 Go 后台服务：

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/client/v1/service/register` | 客户端注册 |
| POST | `/api/client/v1/service/heartbeat` | 心跳 |
| POST | `/api/client/v1/service/report/assets` | 资产上报 |
| POST | `/api/client/v1/service/report/events` | 事件上报 |

### 4.7 开发与构建

```bash
# UI 开发
cd client && npm install && npm run dev

# 后台服务
cd client/service
CLIENT_SERVER_URL=http://localhost:8080 go run ./cmd/service
```

### 4.8 支持平台

| 平台 | UI | 后台服务 | 安装包 |
|------|-----|----------|--------|
| Windows 10/11 | Electron | Go service | `.msi` |
| macOS 12+ | Electron | Go service | `.pkg` |
| Linux | Electron | Go service | `.deb` / `.rpm` |

### 4.9 与管理控制台的区别

| 维度 | 管理控制台 | PC 安全客户端 |
|------|------------|---------------|
| 用户 | 安全管理员 / IT | 普通员工 |
| 形态 | 浏览器网页 | 桌面应用（有窗口） |
| 能力 | 管理全部设备 | 只看本机状态 |
| 后台 | 无 | Go 服务常驻（策略执行） |
| API | `/api/admin/v1` | `/api/client/v1` |

---

## 5. 三端技术对比

| 维度 | 管理控制台 | 手机 App | PC 安全客户端 |
|------|------------|----------|---------------|
| **形态** | Web 网页 | 原生 App | 桌面应用 + 后台服务 |
| **语言** | TypeScript | TypeScript | TypeScript (UI) + Go (服务) |
| **框架** | React + Vite | React Native + Expo | Electron + React |
| **有页面** | 是 | 是 | **是** |
| **API** | `/api/admin/v1` | `/api/app/v1` | `/api/client/v1` |
| **认证** | JWT | JWT | mTLS（服务）+ 本机会话（UI） |
| **分发** | Web 部署 | App Store / APK | MSI / PKG / DEB |

---

## 6. 开发优先级

| 阶段 | 客户端 | 交付 |
|------|--------|------|
| P0 | PC 客户端 UI | 首页、合规、本机信息、设置页面 |
| P0 | PC 客户端服务 | 注册、心跳、资产采集 |
| P0 | 管理控制台 | 登录、设备列表、审计 |
| P1 | 手机 App | 设备概览、告警 |
