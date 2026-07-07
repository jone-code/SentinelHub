# SentinelHub 手机管理 App

iOS / Android 移动端管理应用，对接 `/api/app/v1`。

> 详细技术栈见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | React Native + Expo |
| 语言 | TypeScript |
| UI | React Native Paper |
| 导航 | React Navigation |
| 状态 | Zustand + TanStack Query |
| 安全存储 | expo-secure-store |

## 状态

**规划阶段** — P0 先完成后端 `api.app` 接口，P1 启动 App 开发。

## 核心页面（规划）

| 页面 | 路径 | 说明 |
|------|------|------|
| 登录 | `/login` | JWT 认证 |
| 首页概览 | `/` | 设备数、在线率、告警 |
| 设备列表 | `/devices` | 精简设备信息 |
| 告警 | `/alerts` | 告警列表与确认 |

## API

- 基础路径：`/api/app/v1`
- 示例：`GET /api/app/v1/devices/summary`

## 开发（P1 启动后）

```bash
cd mobile
npm install
npx expo start
```
