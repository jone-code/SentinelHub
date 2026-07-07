# SentinelHub Agent

跨平台终端 Agent，负责资产采集、策略执行与事件上报。

## 目录结构

```
agent/
├── cmd/agent/          # 主程序
├── core/               # 生命周期、配置
├── transport/          # 云端通信 (mTLS)
├── policy/             # 本地策略引擎
├── collectors/         # 数据采集插件
├── enforcers/          # 管控执行插件
│   ├── software/
│   ├── dlp/
│   └── nac/
└── platform/           # OS 特定实现
```

## 运行（开发）

```bash
AGENT_SERVER_URL=http://localhost:8080 go run ./cmd/agent
```

## 构建

```bash
go build -o sentinel-agent ./cmd/agent
```
