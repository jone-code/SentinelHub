# Enforcers（P1+）

策略执行模块占位。软件管控、DLP、NAC 等深度能力通过 `client/native/` sidecar 实现，由 Node 编排层调度。

```
enforcers/
├── software.js   # P1 — 进程拦截（调用 native）
├── dlp.js        # P2 — 文件/USB 管控（调用 native）
└── nac.js        # P2 — 网络准入（调用 native）
```
