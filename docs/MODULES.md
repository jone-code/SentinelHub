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
| `module.policy` | P1 | **CRUD/发布/策略包下发/作用域** |
| `module.software` | P1 | **黑名单检测/阻断/事件上报** |
| `module.compliance` | P1 | **基线扫描/评分/可配置基线** |
| `module.dlp` | P2 | **规则/USB 管控/MinIO 取证** |
| `module.nac` | P2 | **准入策略/合规联动/RADIUS 模板** |
| `module.zerotrust` | P3 | **信任分/受保护应用/历史记录** |
| `module.mdm` | P3 | **配置描述文件/设备分配** |
| `module.asset` | P3 | **租户级资产清单页** |
| `module.remote` | P4 | **远程协助/WebRTC 信令/MinIO 录像** |
| `module.ai` | P4 | **规则引擎安全洞察** |
| 内核驱动 | P4 | **userspace daemon（Unix socket）** |

## 后续增强

1. 内核 minifilter 实装（替换 userspace daemon 后端）
2. LLM 接入 `module.ai`（当前为规则引擎）
3. WebRTC 媒体通道实连
