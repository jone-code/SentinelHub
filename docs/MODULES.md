# SentinelHub 模块索引

## 架构：单体 API + 业务模块分包

只有一个可执行服务 `sentinel-server`（:8080），内部按业务域分包。

### API 接入层

| 包路径 | 路径前缀 | 客户端 |
|--------|----------|--------|
| `api.admin` | `/api/admin/v1` | Web 管理控制台（PC） |
| `api.app` | `/api/app/v1` | 手机管理 App |
| `api.agent` | `/agent/v1` | PC 终端 Agent |

### 业务模块层

| 包路径 | 阶段 | 状态 | 能力 |
|--------|------|------|------|
| `module.identity` | P0 | 骨架 | 租户、用户、RBAC |
| `module.device` | P0 | 骨架 | 设备注册、心跳 |
| `module.asset` | P0 | 骨架 | 软硬件资产 |
| `module.audit` | P0 | 骨架 | 审计日志 |
| `module.policy` | P1 | 骨架 | 策略引擎 |
| `module.software` | P1 | 骨架 | 软件管控 |
| `module.compliance` | P1 | 骨架 | 合规检查 |
| `module.dlp` | P2 | 骨架 | 数据防泄漏 |
| `module.nac` | P2 | 骨架 | 终端准入 |
| `module.zerotrust` | P3 | 骨架 | 零信任 |
| `module.mdm` | P3 | 骨架 | MDM |
| `module.remote` | P3 | 骨架 | 远程控制 |
| `module.ai` | P4 | 预留 | AI 安全 |

### 其他组件

| 目录 | 技术 | 说明 |
|------|------|------|
| `console/` | React + Ant Design | PC 管理控制台 |
| `mobile/` | React Native + Expo | 手机管理 App（规划） |
| `agent/` | Go | PC 终端 Agent |

## 下一步（P0）

1. `module.identity` + `api.admin` 登录/RBAC
2. `module.device` + `api.agent` 注册/心跳
3. `module.asset` 资产采集
4. `module.audit` 审计管道
5. `api.app` 移动端设备概览
