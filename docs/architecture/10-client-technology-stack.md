# 客户端技术栈

SentinelHub 有两类**用户端客户端** + 一类**管理端**：

```
                    ┌─────────────────────────────────┐
                    │     sentinel-server (:8080)      │
                    └───────────┬─────────────────────┘
            ┌───────────────────┼───────────────────┐
            │                   │                   │
   /api/admin/v1        /api/app/v1        /api/client/v1
            │                   │                   │
    ┌───────▼───────┐   ┌───────▼───────────────────────┐
    │  管理控制台     │   │   统一客户端 client/ (Flutter)  │
    │  console/      │   │   ┌─────────┬─────────┐        │
    │  PC 浏览器      │   │   │ 手机端   │  PC 端   │        │
    └───────────────┘   │   │iOS/Andrd│Win/Mac/Lx│        │
                        │   └─────────┴────┬────┘        │
                        │                  │ Go 后台服务  │
                        └──────────────────┴──────────────┘
```

---

## 1. 三端总览

| 客户端 | 目录 | 运行环境 | 技术 | API |
|--------|------|----------|------|-----|
| 管理控制台 | `console/` | PC 浏览器 | React + Ant Design | `/api/admin/v1` |
| **统一客户端** | `client/` | **iOS / Android / Win / macOS / Linux** | **Flutter** | 见下表 |
| PC 后台服务 | `client/service/` | 仅桌面端 | Go | `/api/client/v1/service` |

### 统一客户端 API 分工

| 平台 | UI 调用的 API | 说明 |
|------|---------------|------|
| 手机 iOS/Android | `/api/app/v1` | 移动端精简接口 |
| PC 桌面 Flutter UI | `/api/client/v1` | 本机状态、合规、通知 |
| PC Go 后台服务 | `/api/client/v1/service` | 注册、心跳、上报（无界面） |

---

## 2. 管理控制台（PC Web）— `console/`

面向安全管理员，浏览器访问，**与客户端技术栈独立**。

| 类别 | 技术 |
|------|------|
| 框架 | React 18 + TypeScript |
| 构建 | Vite |
| UI | Ant Design 5 |

```bash
cd console && npm install && npm run dev
```

---

## 3. 统一客户端 — `client/`（Flutter）

**一套代码，编译到手机 + PC 桌面。**

### 3.1 为什么选 Flutter

| 优势 | 说明 |
|------|------|
| **一套代码多端** | iOS、Android、Windows、macOS、Linux 共用 `lib/` |
| **性能与体积** | 比 Electron 轻，无 Chromium 捆绑 |
| **UI 一致** | 手机与 PC 体验统一，自适应布局 |
| **国内生态成熟** | 组件、文档、人才储备充足 |

### 3.2 技术选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | **Flutter** | 3.24+ | 跨平台 UI |
| 语言 | **Dart** | 3.5+ | |
| 状态管理 | Riverpod | 2.x | |
| 路由 | go_router | 14.x | 声明式路由 |
| HTTP | dio | 5.x | API 请求 |
| UI | Material 3 | — | 自适应手机/桌面 |

### 3.3 平台适配策略

```dart
// lib/platform/platform_info.dart
PlatformInfo.isDesktop  → NavigationRail 侧边栏
PlatformInfo.isMobile     → NavigationBar 底部导航
PlatformInfo.apiPrefix    → /api/client/v1 或 /api/app/v1
```

| 平台 | 导航布局 | API 前缀 |
|------|----------|----------|
| iOS / Android | 底部导航栏 | `/api/app/v1` |
| Windows / macOS / Linux | 左侧导航栏 | `/api/client/v1` |

### 3.4 共享页面

| 页面 | 路由 | 手机 | PC |
|------|------|------|-----|
| 首页 | `/` | ✅ | ✅ |
| 合规状态 | `/compliance` | ✅ | ✅ |
| 本机信息 | `/device` | ✅ | ✅ |
| 安全通知 | `/notifications` | ✅ | ✅ |
| 设置 | `/settings` | ✅ | ✅ |

### 3.5 目录结构

```
client/
├── pubspec.yaml
├── lib/
│   ├── main.dart
│   ├── app.dart              # 路由 + 自适应 Shell
│   ├── api/
│   │   └── api_client.dart   # 按平台切换 API 前缀
│   ├── pages/
│   │   ├── home/
│   │   ├── compliance/
│   │   ├── device/
│   │   ├── notifications/
│   │   └── settings/
│   └── platform/
│       └── platform_info.dart
├── android/                    # Android 工程
├── ios/                        # iOS 工程
├── windows/                    # Windows 工程
├── macos/                      # macOS 工程
├── linux/                      # Linux 工程
└── service/                    # PC 专用 Go 后台服务
```

### 3.6 开发与构建

```bash
cd client
flutter pub get

# 开发
flutter run -d windows
flutter run -d android

# 发布构建
flutter build apk --release
flutter build ios --release
flutter build windows --release
flutter build macos --release
flutter build linux --release
```

### 3.7 支持平台

| 平台 | 最低版本 |
|------|----------|
| iOS | 13.0+ |
| Android | API 24（Android 7.0）+ |
| Windows | 10+ |
| macOS | 11+ |
| Linux | 主流发行版（glibc 2.27+） |

---

## 4. PC 后台服务 — `client/service/`（Go）

**仅桌面端（Windows/macOS/Linux）需要**，与 Flutter UI 配合安装。

| 职责 | Flutter UI | Go 服务 |
|------|------------|---------|
| 展示合规分数 | ✅ | |
| 心跳 / 注册 | | ✅ |
| 策略执行 | | ✅ |
| 资产采集 | | ✅ |
| DLP / 管控 | | ✅ |

员工关掉 Flutter 窗口后，Go 服务继续常驻运行。

```bash
cd client/service
CLIENT_SERVER_URL=http://localhost:8080 go run ./cmd/service
```

---

## 5. 三端技术对比

| 维度 | 管理控制台 | 统一客户端（Flutter） | PC Go 服务 |
|------|------------|----------------------|------------|
| **用户** | 管理员 | 员工 / 管理员（移动） | 无界面 |
| **形态** | 浏览器 | 手机 App + 桌面应用 | 系统后台进程 |
| **语言** | TypeScript | Dart | Go |
| **平台** | Web | iOS/Android/Win/Mac/Linux | Win/Mac/Linux |
| **API** | `/api/admin/v1` | `/api/app/v1` 或 `/api/client/v1` | `/api/client/v1/service` |

---

## 6. 与管理控制台的分工

| 能力 | 管理控制台 (React) | 统一客户端 (Flutter) |
|------|-------------------|----------------------|
| 管理全部设备 | ✅ | ❌ |
| 策略编辑 | ✅ | ❌（仅查看本机） |
| 本机合规状态 | ❌ | ✅ |
| 移动查看告警 | ❌ | ✅（手机） |
| 审计查询 | ✅ | ❌ |

---

## 7. 开发优先级

| 阶段 | 交付 |
|------|------|
| P0 | Flutter 骨架 + 共享页面；PC Go 服务注册/心跳 |
| P0 | 管理控制台登录、设备列表 |
| P1 | Flutter 对接真实 API；推送通知（手机） |
| P1 | 管理控制台策略、合规 |
