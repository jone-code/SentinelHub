# API 设计规范

## 1. 总体结构

单一 API 服务 `sentinel-server`（默认 `:8080`），按客户端分三条 API 通道：

```
/api/admin/v1/**    管理端（PC Web 控制台）
/api/app/v1/**      移动端（手机 App）
/api/client/v1/**           PC 安全客户端（UI 页面）
/api/client/v1/service/**   PC 客户端后台服务
/health             健康检查（公共）
```

## 2. 统一响应信封

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "request_id": "req_abc123"
}
```

## 3. 认证

| 通道 | 方式 |
|------|------|
| `/api/admin/v1` | `Authorization: Bearer <JWT>` |
| `/api/app/v1` | `Authorization: Bearer <JWT>` + 可选 `X-Device-Id` |
| `/api/client/v1` | 本机会话 / 设备令牌（UI 页面） |
| `/api/client/v1/service` | mTLS 客户端证书 + `X-Client-Id` |

## 4. 管理端 API（`/api/admin/v1`）

面向 PC 浏览器管理控制台，提供完整管理能力。

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/admin/v1/auth/login` | 管理员登录 |
| GET | `/api/admin/v1/users/me` | 当前用户 |
| GET | `/api/admin/v1/devices` | 设备列表（全量） |
| GET | `/api/admin/v1/devices/{id}` | 设备详情 |
| GET | `/api/admin/v1/assets/software` | 软件资产 |
| GET | `/api/admin/v1/audit/logs` | 审计查询 |
| CRUD | `/api/admin/v1/policies` | 策略管理 |
| GET | `/api/admin/v1/compliance/scans` | 合规扫描 |

## 5. 移动端 API（`/api/app/v1`）

面向 iOS/Android 管理 App，侧重查看、告警、轻量审批。

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/app/v1/auth/login` | 移动端登录 |
| GET | `/api/app/v1/devices` | 我的设备（精简） |
| GET | `/api/app/v1/devices/summary` | 设备概览统计 |
| GET | `/api/app/v1/alerts` | 告警列表 |
| POST | `/api/app/v1/alerts/{id}/ack` | 确认告警 |
| GET | `/api/app/v1/compliance/overview` | 合规概览 |

移动端响应字段较管理端精简，不暴露敏感策略细节。

## 6. PC 安全客户端 API（`/api/client/v1`）

面向安装在员工 PC 上的桌面客户端，分 **UI 接口** 和 **后台服务接口** 两层。

### 6.1 UI 接口 — 供桌面页面展示

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/client/v1/info` | 客户端信息 |
| GET | `/api/client/v1/status` | 安全状态概览（合规分、通知数） |
| GET | `/api/client/v1/compliance` | 合规检查明细 |
| GET | `/api/client/v1/notifications` | 安全通知列表（规划） |

### 6.2 后台服务接口 — 供 Go 服务进程

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/client/v1/service/register` | 首次注册 |
| POST | `/api/client/v1/service/heartbeat` | 心跳与指令拉取 |
| POST | `/api/client/v1/service/report/assets` | 资产上报 |
| POST | `/api/client/v1/service/report/events` | 事件批量上报 |
| GET | `/api/client/v1/service/policy-bundle` | 下载策略包（规划） |

### 心跳响应

```json
{
  "code": 0,
  "data": {
    "server_time": "2026-07-07T16:00:00Z",
    "policy_bundle": { "version": "pol_v1", "hash": "sha256:..." },
    "commands": []
  }
}
```

## 7. 分页与过滤

管理端支持完整分页：

```
GET /api/admin/v1/devices?page=1&page_size=20&status=active
```

移动端默认较小 page_size（如 10），减少流量。

## 8. WebSocket（管理端实时通知）

```
WSS /api/admin/v1/ws?token=...
```

用于控制台实时告警推送。

## 9. 错误码分段

| 范围 | 说明 |
|------|------|
| 0 | 成功 |
| 40000-40999 | 通用客户端错误 |
| 41000-41999 | identity |
| 42000-42999 | device |
| 43000-43999 | policy |
| 50000+ | 服务端错误 |

## 10. 版本演进

- 各通道独立版本：`/api/admin/v1`、`/api/app/v1`、`/api/client/v1`
- 向后兼容至少 2 个大版本
- 客户端协议变更需同步更新 `client/service/` 与 `proto/agent/`
