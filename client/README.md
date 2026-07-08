# SentinelHub 统一客户端（Flutter）

**手机端（iOS/Android）+ PC 端（Windows/macOS/Linux）** 共用一套 Flutter 代码。

> 技术栈详见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 架构

```
client/
├── lib/                    # Flutter UI（手机 + PC 共享）
│   ├── api/                # API 客户端
│   ├── pages/              # 页面
│   └── platform/           # 平台差异（API 路径、布局）
├── android/ ios/           # 移动端工程
├── windows/ macos/ linux/  # 桌面端工程
└── service/                # PC 专用 Go 后台服务（心跳、策略、采集）
```

| 平台 | UI | 后台服务 | API |
|------|-----|----------|-----|
| iOS / Android | Flutter | —（后期可扩展） | `/api/app/v1` |
| Windows / macOS / Linux | Flutter | Go `service/` | `/api/client/v1` + `/api/client/v1/service` |

## 页面（手机 + PC 共享）

| 页面 | 路径 | 说明 |
|------|------|------|
| 首页 | `/` | 安全状态概览 |
| 合规 | `/compliance` | 合规检查项 |
| 本机 | `/device` | 设备信息 |
| 通知 | `/notifications` | 安全通知 |
| 设置 | `/settings` | 服务器、自启动等 |

- **手机**：底部 `NavigationBar`
- **PC**：左侧 `NavigationRail`

## 开发

```bash
# 安装 Flutter SDK 3.24+ 后：
cd client
flutter pub get
flutter run -d windows    # PC
flutter run -d macos
flutter run -d chrome     # Web 调试（可选）
flutter run                 # 连接手机/模拟器
```

## 构建

```bash
flutter build apk          # Android
flutter build ios          # iOS
flutter build windows      # Windows
flutter build macos        # macOS
flutter build linux        # Linux
```

## PC 后台服务（Go）

桌面端另需常驻后台服务，与 Flutter UI 配合：

```bash
cd client/service
CLIENT_SERVER_URL=http://localhost:8080 go run ./cmd/service
```
