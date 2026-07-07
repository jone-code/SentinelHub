# API 设计规范

## 1. 基本原则

- **RESTful** 风格，资源名词复数
- **版本前缀**：`/api/v1`
- **租户隔离**：从 JWT `tenant_id` 注入，禁止客户端指定其他租户
- **统一响应信封**

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "request_id": "req_abc123"
}
```

错误响应：

```json
{
  "code": 40001,
  "message": "device not found",
  "details": { "device_id": "..." },
  "request_id": "req_abc123"
}
```

## 2. 认证

| 调用方 | 方式 |
|--------|------|
| 管理控制台 | `Authorization: Bearer <JWT>` |
| Agent | mTLS 客户端证书 + `X-Agent-ID` |
| 服务间 | gRPC mTLS + metadata `x-tenant-id` |

## 3. 通用约定

### 3.1 分页

```
GET /api/v1/devices?page=1&page_size=20&sort=-created_at
```

响应：

```json
{
  "code": 0,
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "page_size": 20
  }
}
```

### 3.2 过滤

```
GET /api/v1/devices?status=active&os_type=windows&org_unit_id=uuid
GET /api/v1/audit/logs?event_type=dlp.block&start_time=2026-01-01T00:00:00Z
```

### 3.3 批量操作

```
POST /api/v1/devices/batch
{
  "action": "assign_group",
  "device_ids": ["uuid1", "uuid2"],
  "params": { "group_id": "uuid" }
}
```

## 4. 核心 API 清单（P0）

### 身份

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/refresh` | 刷新令牌 |
| GET | `/api/v1/users/me` | 当前用户 |

### 设备

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/devices` | 设备列表 |
| GET | `/api/v1/devices/{id}` | 设备详情 |
| PATCH | `/api/v1/devices/{id}` | 更新（分组、备注） |
| DELETE | `/api/v1/devices/{id}` | 吊销设备 |
| POST | `/api/v1/device-groups` | 创建设备组 |

### Agent（独立前缀 `/agent/v1`）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/agent/v1/register` | 首次注册 |
| POST | `/agent/v1/heartbeat` | 心跳与指令拉取 |
| POST | `/agent/v1/report/assets` | 资产上报 |
| POST | `/agent/v1/report/events` | 事件批量上报 |

### 资产

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/assets/devices/{device_id}` | 设备资产详情 |
| GET | `/api/v1/assets/software` | 软件清单聚合查询 |

### 审计

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/v1/audit/logs` | 审计查询 |
| POST | `/api/v1/audit/export` | 异步导出 |

### 策略（P1）

| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/api/v1/policies` | 列表/创建 |
| GET/PUT | `/api/v1/policies/{id}` | 详情/更新 |
| POST | `/api/v1/policies/{id}/publish` | 发布 |
| GET | `/api/v1/policies/effective` | 查询设备生效策略 |

## 5. Agent 心跳响应结构

```json
{
  "server_time": "2026-07-07T15:00:00Z",
  "config_version": "cfg_v12",
  "policy_bundle": {
    "version": "pol_v45",
    "hash": "sha256:...",
    "url": "/agent/v1/policy-bundle/pol_v45"
  },
  "commands": [
    {
      "id": "cmd_uuid",
      "type": "compliance.scan",
      "params": { "baseline_id": "..." },
      "expires_at": "2026-07-07T16:00:00Z"
    }
  ]
}
```

## 6. WebSocket 事件（控制台）

连接：`WSS /api/v1/ws?token=...`

```json
{
  "type": "alert",
  "payload": {
    "severity": "high",
    "module": "dlp",
    "title": "敏感文件外发被阻断",
    "device_id": "...",
    "timestamp": "..."
  }
}
```

## 7. gRPC 服务定义

Proto 文件位于 `proto/`：

```
proto/
├── common/v1/common.proto
├── identity/v1/identity.proto
├── device/v1/device.proto
├── policy/v1/policy.proto
└── agent/v1/agent.proto
```

命名：`{package}.v1.{Service}/{Method}`

## 8. 错误码分段

| 范围 | 模块 |
|------|------|
| 0 | 成功 |
| 40000-40999 | 通用客户端错误 |
| 41000-41999 | identity |
| 42000-42999 | device |
| 43000-43999 | policy |
| 44000-44999 | compliance |
| 45000-45999 | dlp |
| 50000+ | 服务端内部错误 |

## 9. OpenAPI

- 聚合文档：`api/openapi.yaml`（由各领域 fragment 合并）
- 每个服务维护 `services/{name}/api/openapi.yaml`
- CI 校验 breaking change

## 10. 版本演进

- v1 稳定期内仅 additive 变更
- Breaking change 发布 v2，v1 保留至少 6 个月
- Agent 协议需向后兼容至少 2 个大版本
