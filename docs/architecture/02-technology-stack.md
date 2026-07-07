# 技术选型

## 1. 选型原则

- **私有化友好**：核心组件可离线部署，不依赖公有云专有服务
- **跨平台**：Agent 与部分管控逻辑需覆盖 Windows / macOS / Linux
- **高性能**：终端规模万级时，心跳、日志、策略下发需低延迟
- **可维护**：团队以 Go 为主力语言，降低 Agent 与服务端代码分裂

## 2. 技术栈总览

```
┌─────────────┬──────────────────────────────────────────────────┐
│ 层级         │ 选型                                              │
├─────────────┼──────────────────────────────────────────────────┤
│ 管理控制台   │ React 18, TypeScript, Vite, Ant Design, Zustand  │
│ API 网关     │ Go + gin/echo, OAuth2/OIDC 集成                     │
│ 业务微服务   │ Go 1.22+, gRPC, protobuf                           │
│ 终端 Agent   │ Go, 平台原生能力通过 cgo/系统 API 封装               │
│ 关系型数据库 │ PostgreSQL 16                                      │
│ 缓存         │ Redis 7                                            │
│ 消息队列     │ NATS JetStream                                     │
│ 日志/遥测库  │ ClickHouse                                         │
│ 对象存储     │ MinIO (S3 兼容)                                     │
│ 身份认证     │ Keycloak 或内置 OIDC (可配置)                        │
│ 可观测       │ OpenTelemetry, Prometheus, Grafana, Loki           │
│ 容器编排     │ Docker, Kubernetes (Helm)                          │
│ CI/CD        │ GitHub Actions                                     │
└─────────────┴──────────────────────────────────────────────────┘
```

## 3. 关键选型说明

### 3.1 后端：Go

- Agent 与服务端共享 `pkg/` 协议与模型，减少重复
- 静态编译便于 Agent 分发与跨平台构建
- 并发模型适合高连接网关与事件消费

### 3.2 通信协议

| 场景 | 协议 | 说明 |
|------|------|------|
| 控制台 ↔ 网关 | HTTPS REST + WebSocket | OpenAPI 3 规范，SSE/WS 用于实时告警 |
| 网关 ↔ 微服务 | gRPC (内网 mTLS) | protobuf 定义在 `proto/` |
| Agent ↔ 平台 | HTTPS (长轮询/MQTT 可选) + mTLS | 初期 REST 长轮询，规模大时升级 MQTT |
| 服务间异步 | NATS JetStream | 审计、资产变更、策略下发事件 |

### 3.3 存储分工

| 存储 | 用途 | 保留策略 |
|------|------|----------|
| PostgreSQL | 租户、用户、设备、策略、合规任务 | 长期 |
| Redis | 会话、设备在线状态、策略缓存、分布式锁 | 按 TTL |
| ClickHouse | 审计日志、Agent 遥测、DLP 事件 | 可配置 90天~3年 |
| MinIO | 策略包、Agent 安装包、取证文件、报表导出 | 按业务策略 |

### 3.4 前端

- **Ant Design Pro** 风格的管理台，降低企业级表格/表单开发成本
- **React Query** 管理服务端状态与缓存
- **ECharts** 合规与资产可视化

### 3.5 Agent 架构要点

```
agent/
├── core/           # 生命周期、升级、配置拉取
├── transport/      # 与云端通信、mTLS
├── policy/         # 本地策略引擎（缓存+执行）
├── collectors/     # 资产、软件、合规数据采集
├── enforcers/      # DLP/NAC/软件管控执行插件
└── platform/       # windows/darwin/linux 平台实现
```

插件化 `enforcers` 便于按模块独立开发与灰度发布。

## 4. 不采用的方案（及原因）

| 方案 | 原因 |
|------|------|
| 单体 Java Spring | Agent 与服务端语言分裂，交付包体积大 |
| Elasticsearch 作为主库 | 运维成本高，审计场景 ClickHouse 更合适 |
| Kafka（初期） | 中小规模 NATS 更轻量，后期可桥接 |
| 纯 SaaS 多租户 DB | 私有化客户要求数据完全自控 |

## 5. 开发工具链

- **依赖管理**：Go modules (`go.work` 管理 monorepo)
- **API 生成**：buf + protoc-gen-go
- **代码规范**：golangci-lint, eslint, prettier
- **迁移**：golang-migrate (SQL)
- **本地开发**：docker-compose 一键拉起依赖
