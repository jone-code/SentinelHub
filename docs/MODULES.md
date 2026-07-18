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
| `module.remote` | P4 | **远程协助/WebRTC 媒体/MinIO 录像** |
| `module.ai` | P4 | **规则引擎 + 可选 LLM 摘要** |
| 内核驱动 | P4 | **Linux kmod phase 1 + Windows minifilter skeleton + policy ioctl** |

## 远程协助桌面采集

客户端服务通过 **ffmpeg / grim** 采集真实桌面画面并推送到 WebRTC。Linux 支持多后端自动探测：

| 平台 | 后端 | 条件 / 环境变量 |
|------|------|-----------------|
| Linux (X11) | `x11grab` | `DISPLAY`（默认 `:0.0`）、`REMOTE_CAPTURE_VIDEO_SIZE` |
| Linux (Wayland) | `pipewire` | ffmpeg 编译含 pipewire；`REMOTE_CAPTURE_PIPEWIRE_TARGET`（默认 `screen-capture`） |
| Linux (Wayland) | `wf-recorder` | 安装 wf-recorder；`REMOTE_CAPTURE_WAYLAND_OUTPUT` |
| Linux (Wayland) | `grim` | 安装 grim；`REMOTE_CAPTURE_GRIM_REGION` 可选区域 |
| macOS | `avfoundation` | `REMOTE_CAPTURE_MAC_DEVICE`（默认 `1:none`） |
| Windows | `gdigrab` | `REMOTE_CAPTURE_WIN_INPUT`（默认 `desktop`） |

**自动选择**：Wayland 会话优先 pipewire → wf-recorder → grim → x11；可用 `REMOTE_CAPTURE_BACKEND=auto|x11|pipewire|grim|wf-recorder` 强制指定。

通用：`REMOTE_CAPTURE_WIDTH` / `HEIGHT` / `FPS`；`REMOTE_CAPTURE_SYNTHETIC=true` 强制测试图案。

**依赖**：`ffmpeg`；Wayland 另需 grim 或 wf-recorder 或 pipewire 版 ffmpeg；目标机器需屏幕录制权限。

## 生产级 TURN 部署

跨 NAT 远程协助需 TURN 中继。见 `deploy/coturn/`：

```bash
cd deploy/coturn
export TURN_STATIC_AUTH_SECRET="$(openssl rand -hex 32)"
docker compose up -d
```

后端环境变量（与 coturn 共享密钥）：

| 变量 | 说明 |
|------|------|
| `REMOTE_TURN_URL` | 逗号分隔，如 `turn:host:3478?transport=udp,turn:host:3478?transport=tcp` |
| `REMOTE_TURN_SECRET` | TURN REST 密钥（与 `TURN_STATIC_AUTH_SECRET` 一致） |
| `REMOTE_TURN_CREDENTIAL_TTL` | 临时凭证有效期（秒，默认 86400） |

启用 `REMOTE_TURN_SECRET` 后，后端为每次 `rtc-config` 请求生成临时 username/credential。

## 内核驱动（phase 5）

| 组件 | 能力 |
|------|------|
| Windows minifilter | `IRP_MJ_WRITE` USB 可移动介质写入阻断 |
| Linux BPF | `apply_policy` 自动同步 `blocked_comms` map（bpftool） |
| ClickHouse | 审计冷存储双写（`AUDIT_CH_ENABLED=true`） |
| 管理台 | 安全事件/审计日志分页、驱动事件筛选、冷热存储切换 |

## 平台增强（phase 6）

| 组件 | 能力 |
|------|------|
| Windows 客户端 | `sentinel-native` 通过 `FilterConnectCommunicationPort` 推送策略至 minifilter |
| ClickHouse | `client_events` 冷归档双写 + 管理台 `storage=cold` 查询 |
| NATS | 审计异步管道（JetStream `sentinel.audit.events` → MySQL + ClickHouse） |

### 配置

| 变量 | 说明 |
|------|------|
| `AUDIT_CH_ENABLED` | ClickHouse 冷热双写 |
| `AUDIT_NATS_ENABLED` | 审计走 NATS 异步写入 |
| `CLIENT_EVENTS_NATS_ENABLED` | 客户端事件走 NATS 异步摄入 |
| `NATS_URL` | NATS 连接地址 |

## 平台增强（phase 7）

| 组件 | 能力 |
|------|------|
| NATS | `client_events` 异步摄入（JetStream `sentinel.client.events` → MySQL + ClickHouse + 审计） |
| Windows minifilter | 阻断事件环形缓冲 + `SENTINEL_MSG_DRAIN_EVENTS` 用户态拉取 |
| ClickHouse | `audit_logs` + `client_events` 跨表联合时间线（`GET /api/admin/v1/timeline?storage=cold`） |
| 管理台 | 安全时间线页面（冷存储统一视图） |

## 平台增强（phase 8）

| 组件 | 能力 |
|------|------|
| WebSocket | 驱动事件实时推送至管理台（`WS /api/admin/v1/ws/events?token=`） |
| 时间线 | MySQL 热存储联合查询 + `storage=auto` 冷优先热降级 |
| NATS | 批量消费写入 + `maxAckPending` / stream 字节背压退避 |

### 配置

| 变量 | 说明 |
|------|------|
| `TIMELINE_FALLBACK_TO_HOT` | ClickHouse 不可用时降级 MySQL 时间线 |
| `AUDIT_NATS_BATCH_SIZE` | 审计 NATS 批量拉取大小 |
| `CLIENT_EVENTS_NATS_BATCH_SIZE` | 客户端事件 NATS 批量拉取大小 |
| `*_NATS_MAX_STREAM_BYTES` | Stream 字节上限触发消费退避（0=禁用） |

## 平台增强（phase 9）

| 组件 | 能力 |
|------|------|
| WebSocket | NATS pub-sub 多实例广播（`WS_BROADCAST_NATS_ENABLED`） |
| 时间线 | MySQL → ClickHouse 增量同步（`TIMELINE_SYNC_ENABLED`） |
| NATS | 死信队列 + 消费指标 API（`GET /platform/nats-metrics`） |

### 配置

| 变量 | 说明 |
|------|------|
| `WS_BROADCAST_NATS_ENABLED` | 多实例 WebSocket 广播 |
| `TIMELINE_SYNC_ENABLED` | 热存储增量同步至 ClickHouse |
| `AUDIT_NATS_MAX_DELIVER` | 审计消息最大投递次数后进 DLQ |
| `CLIENT_EVENTS_NATS_MAX_DELIVER` | 客户端事件最大投递次数后进 DLQ |

## 平台增强（phase 10）

| 组件 | 能力 |
|------|------|
| WebSocket | 租户级连接数上限 + 广播速率限流（HTTP 4429 拒绝超限连接） |
| ClickHouse | `ReplacingMergeTree` 去重（`AUDIT_CH_REPLACING_MERGE` + `FINAL` 查询） |
| 可观测性 | Micrometer → `/actuator/prometheus` + 管理台 `metrics-summary` |

### 配置

| 变量 | 说明 |
|------|------|
| `WS_MAX_CONNECTIONS_PER_TENANT` | 每租户 WebSocket 连接上限（默认 10） |
| `WS_MAX_EVENTS_PER_SECOND` | 每租户广播速率上限（默认 50/s） |
| `AUDIT_CH_REPLACING_MERGE` | ClickHouse 使用 ReplacingMergeTree 去重 |

## 平台增强（phase 11）

| 组件 | 能力 |
|------|------|
| WebSocket | 跨租户全局连接池配额（默认 100，超限关闭码 4430） |
| ClickHouse | 存量 MergeTree 表在线迁移至 ReplacingMergeTree（`AUDIT_CH_REPLACING_MERGE_MIGRATE`） |
| Grafana | `deploy/grafana/` 仪表盘模板 + Prometheus 告警规则 |

### 配置

| 变量 | 说明 |
|------|------|
| `WS_MAX_CONNECTIONS_GLOBAL` | 全局 WebSocket 连接上限（默认 100，0=不限） |
| `AUDIT_CH_REPLACING_MERGE_MIGRATE` | 启动时将存量 MergeTree 在线迁移为 ReplacingMergeTree |

## 后续增强

1. WebSocket 连接数按租户套餐分级配额
2. ClickHouse 迁移进度 API 与运维手册
3. Grafana 告警对接 PagerDuty / 钉钉
