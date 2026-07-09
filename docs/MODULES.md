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
| 内核驱动 | P4 | **userspace daemon + enforce driver_assisted 标记** |

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

## 后续增强

1. 内核 minifilter 实装
2. 生产级 TURN 部署（`REMOTE_TURN_*` 环境变量）
