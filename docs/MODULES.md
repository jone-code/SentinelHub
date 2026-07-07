# SentinelHub 模块索引

本文件跟踪各功能模块的实现状态，随开发进度更新。

| 模块 | 服务目录 | 端口 | 阶段 | 状态 | 负责能力 |
|------|----------|------|------|------|----------|
| M01 网关 | `backend/gateway` | 8080 | P0 | 骨架 | API 接入、鉴权、路由 |
| M02 身份 | `backend/identity` | 8081 | P0 | 骨架 | 租户、用户、RBAC |
| M03 设备 | `backend/device` | 8082 | P0 | 待开发 | 设备注册、心跳、分组 |
| M04 资产 | `backend/asset` | 8083 | P0 | 待开发 | 软硬件清单 |
| M05 审计 | `backend/audit` | 8084 | P0 | 待开发 | 审计日志 |
| M06 策略 | `backend/policy` | 8085 | P1 | 骨架 | 策略引擎 |
| M07 软件 | `backend/software` | 8086 | P1 | 骨架 | 软件管控 |
| M08 合规 | `backend/compliance` | 8087 | P1 | 骨架 | 合规检查 |
| M09 DLP | `backend/dlp` | 8088 | P2 | 骨架 | 数据防泄漏 |
| M10 NAC | `backend/nac` | 8089 | P2 | 骨架 | 终端准入 |
| M11 零信任 | `backend/zerotrust` | 8090 | P3 | 骨架 | 零信任访问 |
| M12 MDM | `backend/mdm` | 8091 | P3 | 骨架 | 移动设备管理 |
| M13 远程 | `backend/remote` | 8092 | P3 | 骨架 | 远程控制 |
| M14 AI | `backend/ai` | 8093 | P4 | 预留 | AI 安全能力 |
| Agent | `agent` | — | P0 | 骨架 | 终端 Agent (Go) |
| Console | `console` | 3000 | P0 | 骨架 | 管理控制台 (React) |

## 技术栈

- **后端**：Java 21 + Spring Boot 3.3 + Gradle 多模块
- **Agent**：Go 1.22+（跨平台终端）
- **前端**：React 18 + TypeScript + Ant Design

## 下一步建议

按 [09-roadmap.md](./architecture/09-roadmap.md)，建议从 **P0 模块** 开始：

1. **identity** — 登录、租户、RBAC（Spring Security）
2. **device** — Agent 注册与心跳
3. **asset** — 资产采集与查询
4. **audit** — 审计事件管道（NATS → ClickHouse）
5. **agent** — 完善 transport 与 collectors
6. **console** — 对接真实 API
