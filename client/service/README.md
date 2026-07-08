# PC 客户端后台服务（Node.js）

运行在 Windows / macOS / Linux 上的**后台进程**，与 Flutter 桌面 UI 配合使用。

- 无界面，负责心跳、策略同步、资产采集、管控执行
- 用户关闭 Flutter 窗口后仍可继续运行

## 技术栈

| 类别 | 技术 |
|------|------|
| 运行时 | Node.js 20+ |
| 语言 | JavaScript (ESM) |
| HTTP | 原生 fetch |
| API | `/api/client/v1/service/*` |

## 运行

```bash
cd client/service
npm install   # 无第三方依赖时可跳过
npm start

# 开发（文件变更自动重启）
npm run dev
```

环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `CLIENT_SERVER_URL` | `http://localhost:8080` | 云端 API 地址 |
| `CLIENT_ID` | — | 客户端 ID |
| `CLIENT_HEARTBEAT_INTERVAL_SEC` | `60` | 心跳间隔（秒） |

## 与 Flutter 的关系

```
PC 安装包
├── Flutter 桌面应用（有界面）
└── Node 后台服务（本目录，系统服务 / 托盘拉起）
```

后期可由 Flutter 桌面端在启动时拉起本服务，或注册为系统服务独立运行。

## 目录规划

```
service/
├── package.json
└── src/
    ├── index.js      # 入口
    ├── config.js     # 配置
    └── service.js    # 心跳、注册、上报
    # collectors/    # 资产采集（P0）
    # enforcers/     # 策略执行（P1）
```
