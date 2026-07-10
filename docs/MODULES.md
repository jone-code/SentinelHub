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

## 内核驱动（phase 2）

| 平台 | 能力 |
|------|------|
| Linux kmod | 事件 ring buffer（`PUSH_EVENT` / `GET_EVENT` ioctl） |
| Linux daemon | **fanotify** 文件打开钩子（`sensitive_path` / `file_hook` 规则，`block` 动作拒绝访问） |
| Windows | minifilter **通信端口**骨架（`\\SentinelHubPort`） |

Daemon IPC 新增：`get_events`、`drain_kernel_events`。

## 后续增强

1. Linux LSM BPF 进程拦截
2. Windows minifilter 策略缓存 + 路径阻断实装
3. 内核事件实时推送至 Node 服务 / 后端审计
