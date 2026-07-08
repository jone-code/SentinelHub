# PC 客户端后台服务（Node.js）

运行在 Windows / macOS / Linux 上的**编排层进程**，与 Flutter 桌面 UI 配合使用。

```
Flutter UI  ──HTTP──►  Node service (:39201)  ──spawn──►  sentinel-native
                              │
                              └──HTTPS──►  云端 /api/client/v1/service/*
```

- **Node**：云端通信、本地 IPC、调度 native sidecar
- **sentinel-native**（`client/native/`）：深度系统采集与管控（P1+）

## 技术栈

| 类别 | 技术 |
|------|------|
| 运行时 | Node.js 20+ |
| 语言 | JavaScript (ESM) |
| 本地 IPC | HTTP `127.0.0.1:39201` |
| Native | Rust sidecar（可选，自动探测） |
| 云端 API | `/api/client/v1/service/*` |

## 运行

```bash
cd client/service
npm start

# 开发（文件变更自动重启）
npm run dev
```

可选：编译 native sidecar 后 Node 会自动优先使用

```bash
cd client/native && cargo build --release
```

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `CLIENT_SERVER_URL` | `http://localhost:8080` | 云端 API 地址 |
| `CLIENT_ID` | — | 客户端 ID（空则自动注册） |
| `CLIENT_TENANT_TOKEN` | — | 租户注册令牌 |
| `CLIENT_HEARTBEAT_INTERVAL_SEC` | `60` | 心跳间隔（秒） |
| `CLIENT_ASSET_INTERVAL_SEC` | `300` | 资产采集间隔（秒） |
| `CLIENT_LOCAL_HOST` | `127.0.0.1` | 本地 IPC 绑定地址 |
| `CLIENT_LOCAL_PORT` | `39201` | 本地 IPC 端口 |
| `SENTINEL_NATIVE_BIN` | — | native 二进制路径（可选） |

## 本地 IPC（供 Flutter 桌面端）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/local/status` | 服务状态、云端连接、native 是否可用 |
| GET | `/local/assets` | 最近一次资产快照 |

## 目录结构

```
service/
├── package.json
└── src/
    ├── index.js
    ├── config.js
    ├── service.js          # 编排：注册、心跳、采集、上报
    ├── local-server.js     # Flutter ↔ Node IPC
    ├── native-bridge.js    # sentinel-native sidecar 调度
    ├── collectors/         # Node fallback 采集
    └── enforcers/            # P1+ 策略执行（调 native）
```

## 与 Flutter 的关系

员工关掉 Flutter 窗口后，Node 服务继续常驻运行。Flutter 通过 `http://127.0.0.1:39201/local/status` 读取真实服务状态。
