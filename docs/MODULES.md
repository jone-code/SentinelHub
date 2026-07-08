# SentinelHub 模块索引

## 客户端架构

| 端 | 目录 | 技术 | API |
|----|------|------|-----|
| 管理控制台 | `console/` | React + Ant Design | `/api/admin/v1` |
| **手机 + PC 客户端** | `client/` | **Flutter** | `/api/app/v1`（手机）/ `/api/client/v1`（PC） |
| PC 后台服务 | `client/service/` | Node.js + Rust native | `/api/client/v1/service` |

> 手机与 PC **共用** `client/lib/` Flutter 代码，按平台自适应布局与 API。

## 业务模块（后端 `module.*`）

| 包路径 | 阶段 | 状态 |
|--------|------|------|
| `module.identity` | P0 | **登录/JWT/种子数据** |
| `module.device` | P0 | **注册/心跳/列表** |
| `module.asset` | P0 | **资产上报/入库** |
| `module.audit` | P0 | **审计写入/查询** |
| `module.policy` | P1 | **CRUD/发布/策略包下发** |
| 其他模块 | P1~P4 | 骨架 |

## 下一步（P1 继续）

1. Rust enforcer — 软件黑名单进程检测
2. `module.compliance` 合规扫描与真实评分
3. 策略作用域（按设备组/组织）
