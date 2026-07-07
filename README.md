# SentinelHub

企业级安全办公平台 — 统一终端管控、数据防泄漏、网络准入与零信任访问。

## 能力模块

| 模块 | 说明 | 阶段 |
|------|------|------|
| 设备管控 | 终端注册、分组、在线状态、指令下发 | P0 |
| 资产管理 | 软硬件清单、变更检测 | P0 |
| 审计日志 | 统一审计、查询、导出 | P0 |
| 软件管控 | 黑白名单、安装/运行拦截 | P1 |
| 合规检查 | 基线扫描、合规评分 | P1 |
| 策略引擎 | 统一策略模型与下发 | P1 |
| DLP | 数据防泄漏、通道管控 | P2 |
| NAC | 终端网络准入 | P2 |
| 零信任 | 持续验证、应用访问控制 | P3 |
| MDM | 移动设备管理 | P3 |
| 远程控制 | 远程桌面、会话审计 | P3 |
| AI 安全 | 异常检测、智能查询（预留） | P4 |

## 架构文档

完整设计见 [docs/architecture/](./docs/architecture/)：

- [01-overview.md](./docs/architecture/01-overview.md) — 整体概览
- [02-technology-stack.md](./docs/architecture/02-technology-stack.md) — 技术选型
- [03-system-architecture.md](./docs/architecture/03-system-architecture.md) — 系统架构
- [04-module-design.md](./docs/architecture/04-module-design.md) — 模块设计
- [05-data-model.md](./docs/architecture/05-data-model.md) — 数据模型
- [06-api-design.md](./docs/architecture/06-api-design.md) — API 规范
- [07-deployment.md](./docs/architecture/07-deployment.md) — 部署架构
- [08-security.md](./docs/architecture/08-security.md) — 安全架构
- [09-roadmap.md](./docs/architecture/09-roadmap.md) — 建设路线图

## 仓库结构

```
SentinelHub/
├── agent/              # 终端 Agent（Go）
├── console/            # 管理控制台（React）
├── deploy/             # Docker Compose / Helm / 迁移脚本
├── docs/               # 架构与设计文档
├── pkg/                # 跨服务共享库
├── proto/              # Protobuf / gRPC 定义
└── services/           # 后端微服务
    ├── gateway/
    ├── identity/
    ├── device/
    ├── asset/
    ├── audit/
    ├── policy/
    ├── software/
    ├── compliance/
    ├── dlp/
    ├── nac/
    ├── zerotrust/
    ├── mdm/
    ├── remote/
    └── ai/
```

## 快速开始

### 前置要求

- Go 1.22+
- Node.js 20+
- Docker & Docker Compose

### 启动开发环境

```bash
# 启动基础设施（PostgreSQL, Redis, NATS, ClickHouse, MinIO）
make dev-up

# 构建所有服务
make build

# 启动平台服务（开发模式）
make dev-services
```

访问：
- 控制台：http://localhost:3000
- API 网关：http://localhost:8080
- API 健康检查：http://localhost:8080/health

## 开发规范

- 模块开发顺序见 [09-roadmap.md](./docs/architecture/09-roadmap.md)
- 每个服务需包含：`cmd/`、`internal/`、`migrations/`、`README.md`
- 服务间通过 gRPC / NATS 通信，禁止跨库访问
- 所有安全操作必须产生审计事件

## License

Proprietary — All rights reserved.
