# SentinelHub PC 终端 Agent

运行在 Windows / macOS / Linux 上的终端客户端，对接 `/agent/v1`。

> 详细技术栈见 [docs/architecture/10-client-technology-stack.md](../docs/architecture/10-client-technology-stack.md)

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Go 1.22+ |
| 通信 | HTTPS + mTLS |
| 协议 | JSON / Protobuf（`proto/agent/`） |
| 本地存储 | BoltDB / SQLite（规划） |
| 日志 | slog（标准库） |
| 运行方式 | 系统服务（Windows Service / launchd / systemd） |

## 支持平台

| 平台 | 安装包 |
|------|--------|
| Windows 10/11 | `.msi` |
| macOS 12+ | `.pkg` |
| Linux（Ubuntu/CentOS） | `.deb` / `.rpm` |

## 目录结构

```
agent/
├── cmd/agent/          # 主程序
├── core/               # 生命周期、配置
├── transport/          # 云端通信 (mTLS)
├── policy/             # 本地策略引擎
├── collectors/         # 数据采集
├── enforcers/          # 管控插件（software/dlp/nac）
└── platform/           # windows/darwin/linux
```

## 开发与构建

```bash
# 开发
AGENT_SERVER_URL=http://localhost:8080 go run ./cmd/agent

# 构建
go build -o sentinel-agent ./cmd/agent

# 交叉编译
GOOS=windows GOARCH=amd64 go build -o dist/sentinel-agent.exe ./cmd/agent
GOOS=darwin GOARCH=arm64 go build -o dist/sentinel-agent-darwin ./cmd/agent
GOOS=linux GOARCH=amd64 go build -o dist/sentinel-agent-linux ./cmd/agent
```

## 资源目标

- 安装包 &lt; 30 MB
- 内存 &lt; 80 MB（空闲）
- 心跳间隔 60s
