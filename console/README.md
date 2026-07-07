# SentinelHub 管理控制台

PC 端 Web 管理后台，对接 `/api/admin/v1`。

> 详细技术栈见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| 语言 | TypeScript | 5.x |
| 构建 | Vite | 5.x |
| UI | Ant Design | 5.x |
| 路由 | React Router | 6.x |
| 状态（规划） | Zustand + TanStack Query | — |
| 图表（规划） | ECharts | — |
| HTTP（规划） | Axios | — |

## 页面模块

| 页面 | 路径 | 阶段 |
|------|------|------|
| 仪表盘 | `/` | P0 |
| 设备管控 | `/devices` | P0 |
| 资产管理 | `/assets` | P0 |
| 审计日志 | `/audit` | P0 |
| 策略管理 | `/policies` | P1 |
| 合规检查 | `/compliance` | P1 |
| DLP | `/dlp` | P2 |

## 开发

```bash
npm install
npm run dev       # http://localhost:3000，API 代理至 :8080
npm run build     # 构建静态资源 dist/
```

## 浏览器要求

Chrome 90+、Edge 90+、Firefox 90+，最低分辨率 1280×720。
