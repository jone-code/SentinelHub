# SentinelHub 模块索引

## 客户端架构

| 端 | 目录 | 技术 | API |
|----|------|------|-----|
| 管理控制台 | `console/` | React + Ant Design | `/api/admin/v1` |
| **手机 + PC 客户端** | `client/` | **Flutter** | `/api/app/v1`（手机）/ `/api/client/v1`（PC） |
| PC 后台服务 | `client/service/` | Go | `/api/client/v1/service` |

> 手机与 PC **共用** `client/lib/` Flutter 代码，按平台自适应布局与 API。

## 业务模块（后端 `module.*`）

| 包路径 | 阶段 | 状态 |
|--------|------|------|
| `module.identity` | P0 | 骨架 |
| `module.device` | P0 | 骨架 |
| `module.asset` | P0 | 骨架 |
| `module.audit` | P0 | 骨架 |
| `module.policy` | P1 | 骨架 |
| 其他模块 | P1~P4 | 骨架 |

## 下一步（P0）

1. `module.identity` + 管理端登录
2. `module.device` + PC Go 服务注册/心跳
3. Flutter 对接 `/api/client/v1` 与 `/api/app/v1`
4. 管理控制台设备列表
