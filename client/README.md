# SentinelHub 统一客户端（Flutter）

**手机端（iOS/Android）+ PC 端（Windows/macOS/Linux）** 共用一套 Flutter 代码。

> 技术栈详见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 架构

```
client/
├── lib/                    # Flutter UI（手机 + PC 共享）
├── android/ ios/           # 移动端
├── windows/ macos/ linux/  # 桌面端
└── service/                # PC 专用 Node.js 后台服务
```

| 平台 | UI | 后台服务 | API |
|------|-----|----------|-----|
| iOS / Android | Flutter | — | `/api/app/v1` |
| Windows / macOS / Linux | Flutter | **Node.js** `service/` | `/api/client/v1` + `/api/client/v1/service` |

## 开发

```bash
# Flutter UI
cd client && flutter pub get && flutter run -d windows

# PC 后台服务（另开终端）
cd client/service && npm start
```

## 构建

```bash
flutter build apk
flutter build windows
# Node 服务随安装包捆绑，见 service/README.md
```
