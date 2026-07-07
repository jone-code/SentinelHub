# SentinelHub 模块索引

本文件跟踪各功能模块的实现状态，随开发进度更新。

| 模块 | 服务目录 | 阶段 | 状态 | 负责能力 |
|------|----------|------|------|----------|
| M01 网关 | `services/gateway` | P0 | 骨架 | API 接入、鉴权、路由 |
| M02 身份 | `services/identity` | P0 | 骨架 | 租户、用户、RBAC |
| M03 设备 | `services/device` | P0 | 待开发 | 设备注册、心跳、分组 |
| M04 资产 | `services/asset` | P0 | 待开发 | 软硬件清单 |
| M05 审计 | `services/audit` | P0 | 待开发 | 审计日志 |
| M06 策略 | `services/policy` | P1 | 骨架 | 策略引擎 |
| M07 软件 | `services/software` | P1 | 骨架 | 软件管控 |
| M08 合规 | `services/compliance` | P1 | 骨架 | 合规检查 |
| M09 DLP | `services/dlp` | P2 | 骨架 | 数据防泄漏 |
| M10 NAC | `services/nac` | P2 | 骨架 | 终端准入 |
| M11 零信任 | `services/zerotrust` | P3 | 骨架 | 零信任访问 |
| M12 MDM | `services/mdm` | P3 | 骨架 | 移动设备管理 |
| M13 远程 | `services/remote` | P3 | 骨架 | 远程控制 |
| M14 AI | `services/ai` | P4 | 预留 | AI 安全能力 |
| Agent | `agent` | P0 | 骨架 | 终端 Agent |
| Console | `console` | P0 | 骨架 | 管理控制台 |

## 下一步建议

按 [09-roadmap.md](./architecture/09-roadmap.md)，建议从 **P0 模块** 开始：

1. **identity** — 登录、租户、RBAC
2. **device** — Agent 注册与心跳
3. **asset** — 资产采集与查询
4. **audit** — 审计事件管道
5. **agent** — 完善 transport 与 collectors
6. **console** — 对接真实 API
