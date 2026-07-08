# SentinelHub

企业级安全办公平台 — 统一终端管控、数据防泄漏、网络准入与零信任访问。

## 架构概览

**单体 API 服务 + 业务模块分包**，对外统一入口，支持三端客户端：

| 端 | API 前缀 | 客户端 |
|----|----------|--------|
| 管理端 | `/api/admin/v1` | Web 控制台（PC 浏览器） |
| 手机端 | `/api/app/v1` | Flutter 客户端（iOS/Android） |
| PC 端 | `/api/client/v1` | Flutter 客户端 + Node.js 后台服务 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.3, Maven |
| 管理控制台 | React 18, TypeScript, Vite, Ant Design |
| 手机 + PC 客户端 | Flutter (Dart) |
| PC 后台服务 | Node.js 20+（仅桌面端常驻） |
| 存储 | MySQL, Redis, ClickHouse, MinIO |

> 客户端详见 [docs/architecture/10-client-technology-stack.md](./docs/architecture/10-client-technology-stack.md)（Flutter 统一手机+PC）

## 仓库结构

```
SentinelHub/
├── backend/
│   ├── common/         # 公共库
│   └── server/         # 统一 API 服务
│       ├── api/admin/  # 管理端 API
│       ├── api/app/    # 移动端 API
│       ├── api/client/ # PC 客户端 API（UI + 后台服务）
│       └── module/     # 业务模块（device, asset, ...）
├── console/            # PC 管理控制台（React）
├── client/             # 统一客户端 Flutter（iOS/Android/Win/Mac/Linux）
│   ├── lib/            # 共享 UI 代码
│   └── service/        # PC 专用 Node.js 后台服务
├── deploy/             # Docker Compose / 迁移脚本
└── docs/               # 架构文档
```

## 快速开始

```bash
make dev-up                              # 启动基础设施
cd backend && ./mvnw -pl server spring-boot:run   # 启动 API 服务
cd console && npm install && npm run dev        # 启动控制台
```

验证：
```bash
curl http://localhost:8080/health
curl http://localhost:8080/api/admin/v1/info
curl http://localhost:8080/api/app/v1/info
curl http://localhost:8080/api/client/v1/info
curl http://localhost:8080/api/client/v1/status
```

## 架构文档

见 [docs/architecture/](./docs/architecture/)。

## License

Proprietary — All rights reserved.
