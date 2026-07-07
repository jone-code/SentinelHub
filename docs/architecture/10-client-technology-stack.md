# 客户端技术栈

SentinelHub 有三类客户端，分别对接统一 API 服务的不同通道。

```
                    ┌─────────────────────────────────┐
                    │     sentinel-server (:8080)      │
                    └───────────┬─────────────────────┘
            ┌───────────────────┼───────────────────┐
            │                   │                   │
   /api/admin/v1        /api/app/v1          /agent/v1
            │                   │                   │
    ┌───────▼───────┐   ┌───────▼───────┐   ┌───────▼───────┐
    │  管理控制台     │   │  手机管理 App  │   │  PC 终端 Agent │
    │  console/      │   │  mobile/       │   │  agent/        │
    │  PC 浏览器      │   │  iOS/Android   │   │  Win/macOS/Linux│
    └───────────────┘   └───────────────┘   └───────────────┘
```

---

## 1. 三端总览

| 客户端 | 目录 | 运行环境 | API 通道 | 阶段 |
|--------|------|----------|----------|------|
| 管理控制台 | `console/` | PC 浏览器（Chrome/Edge/Firefox） | `/api/admin/v1` | P0 |
| 手机管理 App | `mobile/` | iOS / Android | `/api/app/v1` | P0（API 先行）/ P1（App 开发） |
| PC 终端 Agent | `agent/` | Windows / macOS / Linux | `/agent/v1` | P0 |

---

## 2. 管理控制台（PC Web）— `console/`

面向安全管理员、IT 运维人员，提供完整的策略配置、设备管理、合规报表、审计查询能力。

### 2.1 技术选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | React | 18.x | 组件化 UI |
| 语言 | TypeScript | 5.x | 类型安全 |
| 构建 | Vite | 5.x | 快速开发与 HMR |
| UI 组件库 | Ant Design | 5.x | 企业级中后台组件 |
| 路由 | React Router | 6.x | 单页应用路由 |
| 状态管理 | Zustand | 4.x（规划） | 轻量全局状态 |
| 服务端状态 | TanStack Query (React Query) | 5.x（规划） | API 缓存与请求管理 |
| 图表 | ECharts | 5.x（规划） | 合规/资产可视化 |
| HTTP | Axios | 1.x（规划） | 请求封装、拦截器 |
| 代码规范 | ESLint + Prettier | — | 统一风格 |

### 2.2 目录结构

```
console/
├── index.html
├── package.json
├── vite.config.ts          # 开发代理 → localhost:8080
├── tsconfig.json
└── src/
    ├── main.tsx
    ├── App.tsx             # 布局 + 路由
    ├── pages/              # 页面（按业务模块）
    │   ├── dashboard/
    │   ├── devices/
    │   ├── assets/
    │   ├── policies/
    │   ├── compliance/
    │   ├── audit/
    │   └── ...
    ├── components/         # 通用组件
    ├── services/           # API 客户端（对接 /api/admin/v1）
    ├── stores/             # Zustand stores
    ├── hooks/
    └── utils/
```

### 2.3 API 对接

- 基础路径：`/api/admin/v1`
- 开发代理：Vite 将 `/api` 代理至 `http://localhost:8080`
- 认证：`Authorization: Bearer <JWT>`
- 实时通知：WebSocket `WSS /api/admin/v1/ws`（P1）

### 2.4 开发与构建

```bash
cd console
npm install
npm run dev       # http://localhost:3000
npm run build     # 输出 dist/，由 Nginx 静态托管
```

### 2.5 浏览器要求

- Chrome 90+、Edge 90+、Firefox 90+
- 分辨率：最低 1280×720，推荐 1920×1080

---

## 3. 手机管理 App — `mobile/`

面向安全负责人、IT 管理员移动办公，提供设备概览、告警处置、轻量审批，不做完整策略编辑。

### 3.1 技术选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 跨端框架 | **React Native** | 0.76.x | 与控制台共享 React/TS 生态 |
| 开发工具链 | Expo | 52.x | 简化构建、OTA 更新、推送集成 |
| 语言 | TypeScript | 5.x | 与 console 统一 |
| UI 组件库 | React Native Paper 或 Tamagui | — | Material 风格，适配 iOS/Android |
| 导航 | React Navigation | 6.x | 栈 / 标签 / 抽屉导航 |
| 状态管理 | Zustand | 4.x | 与控制台一致 |
| 服务端状态 | TanStack Query | 5.x | API 缓存 |
| 安全存储 | expo-secure-store | — | Token 安全存储 |
| 推送 | Expo Notifications + FCM/APNs | — | 告警实时推送 |
| HTTP | Axios | 1.x | 对接 `/api/app/v1` |

> **选型理由**：团队已使用 React 开发控制台，React Native 可复用 TypeScript 类型定义与 API 客户端逻辑，降低维护成本。若后续对性能或 UI 一致性有更高要求，可评估 Flutter 备选方案。

### 3.2 支持平台

| 平台 | 最低版本 |
|------|----------|
| iOS | 14.0+ |
| Android | API 26（Android 8.0）+ |

### 3.3 目录结构（规划）

```
mobile/
├── app.json                # Expo 配置
├── package.json
├── tsconfig.json
└── src/
    ├── app/                # 页面（Expo Router）
    │   ├── (tabs)/
    │   │   ├── index.tsx       # 首页概览
    │   │   ├── devices.tsx
    │   │   └── alerts.tsx
    │   └── login.tsx
    ├── components/
    ├── services/           # API 客户端（对接 /api/app/v1）
    ├── stores/
    └── types/              # 可与 console 共享类型定义
```

### 3.4 API 对接

- 基础路径：`/api/app/v1`
- 认证：JWT + 可选生物识别（Face ID / 指纹）解锁本地 Token
- 推送：服务端告警 → FCM/APNs → App 通知栏

### 3.5 开发与构建

```bash
cd mobile
npm install
npx expo start          # 开发调试
eas build --platform ios     # 生产构建 iOS
eas build --platform android # 生产构建 Android
```

### 3.6 与管理控制台的差异

| 能力 | 管理控制台 | 手机 App |
|------|------------|----------|
| 策略编辑 | 完整 | 不支持（P1 仅查看） |
| 设备列表 | 全量 + 导出 | 精简列表 + 概览 |
| 告警处置 | 支持 | 支持（核心场景） |
| 审计查询 | 完整筛选 | 最近告警/事件 |
| 远程控制 | 支持（P3） | 不支持 |

---

## 4. PC 终端 Agent — `agent/`

运行在员工 PC（Windows/macOS/Linux）上的轻量客户端，负责资产采集、策略执行、事件上报。

### 4.1 技术选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Go | 1.22+ | 单二进制、低资源、跨平台 |
| 构建 | Go modules | — | 静态编译 |
| 通信 | HTTPS + mTLS | — | 对接 `/agent/v1` |
| 序列化 | JSON / Protobuf | — | 协议定义见 `proto/agent/` |
| 本地存储 | BoltDB 或 SQLite | — | 策略缓存、离线队列 |
| 日志 | zap / slog | — | 结构化日志 |
| 服务化 | 系统服务 | — | Windows Service / launchd / systemd |

### 4.2 支持平台

| 平台 | 架构 | 安装包格式 |
|------|------|------------|
| Windows | amd64, arm64 | `.msi` / `.exe` |
| macOS | universal (Intel + Apple Silicon) | `.pkg` |
| Linux | amd64, arm64 | `.deb` / `.rpm` |

### 4.3 目录结构

```
agent/
├── cmd/agent/              # 主程序入口
├── core/                   # 生命周期、配置、升级
├── transport/              # HTTP/mTLS 通信
├── policy/                 # 本地策略引擎
├── collectors/             # 资产/软件/合规采集
├── enforcers/              # 管控执行插件
│   ├── software/
│   ├── dlp/
│   └── nac/
└── platform/               # OS 特定实现
    ├── windows/
    ├── darwin/
    └── linux/
```

### 4.4 API 对接

- 基础路径：`/agent/v1`
- 认证：mTLS 客户端证书（注册时签发）
- 核心接口：
  - `POST /agent/v1/register` — 首次注册
  - `POST /agent/v1/heartbeat` — 心跳（默认 60s）
  - `POST /agent/v1/report/assets` — 资产上报
  - `POST /agent/v1/report/events` — 事件上报

### 4.5 开发与构建

```bash
# 开发运行
AGENT_SERVER_URL=http://localhost:8080 go run ./cmd/agent

# 交叉编译
GOOS=windows GOARCH=amd64 go build -o dist/sentinel-agent.exe ./cmd/agent
GOOS=darwin GOARCH=arm64 go build -o dist/sentinel-agent-darwin ./cmd/agent
GOOS=linux GOARCH=amd64 go build -o dist/sentinel-agent-linux ./cmd/agent
```

### 4.6 资源占用目标

| 指标 | 目标 |
|------|------|
| 安装包大小 | &lt; 30 MB |
| 内存占用 | &lt; 80 MB（空闲） |
| CPU 占用 | &lt; 1%（空闲） |
| 心跳间隔 | 60s（可配置） |

---

## 5. 三端技术对比

| 维度 | 管理控制台 | 手机 App | PC Agent |
|------|------------|----------|----------|
| **语言** | TypeScript | TypeScript | Go |
| **框架** | React 18 | React Native + Expo | 自研 |
| **UI** | Ant Design | RN Paper / Tamagui | 无 UI（系统托盘可选） |
| **构建工具** | Vite | Expo EAS | Go build |
| **分发方式** | Web / 内网静态部署 | App Store / 企业签名 APK | MSI/PKG/DEB |
| **API 前缀** | `/api/admin/v1` | `/api/app/v1` | `/agent/v1` |
| **认证** | JWT | JWT + Secure Store | mTLS 证书 |
| **离线能力** | 否 | 有限缓存 | 策略本地执行 |

---

## 6. 共享与复用

```
console/src/types/  ──┐
                      ├──  API 类型定义、DTO（通过 monorepo 或 npm workspace 共享）
mobile/src/types/   ──┘

proto/agent/        ────  Agent 协议（Go + Java 共用）

console/src/services/ ── 可参考复用──▶ mobile/src/services/
                          （admin 与 app API 字段有差异，需适配层）
```

---

## 7. 版本与依赖锁定策略

- **控制台**：`package-lock.json` 锁定，CI 使用 `npm ci`
- **手机 App**：`package-lock.json` + Expo SDK 固定版本
- **Agent**：`go.sum` 锁定，Go 版本在 `go.mod` 声明

---

## 8. 开发优先级

| 阶段 | 客户端 | 交付 |
|------|--------|------|
| P0 | 管理控制台 | 登录、设备列表、资产、审计 |
| P0 | PC Agent | 注册、心跳、资产采集 |
| P0 | 手机 App API | 后端 `api.app` 接口就绪 |
| P1 | 手机 App | 设备概览、告警列表、登录 |
| P1 | 管理控制台 | 策略编辑、合规仪表盘 |
| P2+ | 三端 | DLP/NAC 相关能力按模块迭代 |
