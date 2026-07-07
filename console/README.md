# SentinelHub 管理控制台

React + TypeScript + Ant Design 管理后台。

## 页面模块（按路线图）

| 页面 | 路径 | 阶段 |
|------|------|------|
| 仪表盘 | `/` | P0 |
| 设备管控 | `/devices` | P0 |
| 资产管理 | `/assets` | P0 |
| 审计日志 | `/audit` | P0 |
| 策略管理 | `/policies` | P1 |
| 合规检查 | `/compliance` | P1 |
| DLP | `/dlp` | P2 |
| NAC | `/nac` | P2 |
| 零信任 | `/zerotrust` | P3 |
| MDM | `/mdm` | P3 |
| 远程控制 | `/remote` | P3 |

## 开发

```bash
npm install
npm run dev
```

API 请求通过 Vite 代理转发至 Gateway (`localhost:8080`)。
