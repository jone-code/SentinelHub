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

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.3, Gradle, MyBatis-Plus |
| 前端 | React 18, TypeScript, Ant Design |
| Agent | Go 1.22+（跨平台终端） |
| 存储 | PostgreSQL, Redis, ClickHouse, MinIO |
| 消息 | NATS JetStream |

## 架构文档

完整设计见 [docs/architecture/](./docs/architecture/)。

## 仓库结构

```
SentinelHub/
├── backend/            # Java 后端（Spring Boot 多模块）
│   ├── common/         # 公共库
│   ├── gateway/        # API 网关 :8080
│   ├── identity/       # 身份租户 :8081
│   ├── device/         # 设备管控 :8082
│   └── ...             # 其他业务模块
├── agent/              # 终端 Agent（Go）
├── console/            # 管理控制台（React）
├── deploy/             # Docker Compose / Helm / 迁移脚本
├── docs/               # 架构与设计文档
└── proto/              # Protobuf / gRPC 定义
```

## 快速开始

### 前置要求

- Java 21+
- Go 1.22+（仅 Agent 开发）
- Node.js 20+（控制台）
- Docker & Docker Compose

### 启动开发环境

```bash
# 启动基础设施（PostgreSQL, Redis, NATS, ClickHouse, MinIO）
make dev-up

# 构建后端
cd backend && ./gradlew build

# 启动网关
./gradlew :gateway:bootRun

# 启动控制台
cd console && npm install && npm run dev
```

访问：
- 控制台：http://localhost:3000
- API 网关：http://localhost:8080/health

## 开发规范

见 [docs/CONTRIBUTING.md](./docs/CONTRIBUTING.md)。

## License

Proprietary — All rights reserved.
