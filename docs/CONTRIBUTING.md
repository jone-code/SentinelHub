# 模块开发指南

## 新模块 Checklist

开发新功能模块前，请确认以下事项：

- [ ] 阅读 [04-module-design.md](../architecture/04-module-design.md) 中对应模块定义
- [ ] 在 `services/{module}/` 创建服务骨架
- [ ] 定义 `proto/{module}/v1/*.proto`（如需 gRPC）
- [ ] 编写 `migrations/` SQL 迁移
- [ ] 实现 REST API（遵循 [06-api-design.md](../architecture/06-api-design.md)）
- [ ] 发布 NATS 事件，审计事件使用 `pkg/audit.Event`
- [ ] 更新 `console/src/pages/{module}/`
- [ ] 补充单元测试与 README

## 服务目录结构

```
services/{module}/
├── cmd/{module}/main.go       # 入口
├── internal/
│   ├── config/                # 服务配置
│   ├── handler/               # HTTP handlers
│   ├── service/               # 业务逻辑
│   └── repository/          # 数据访问
├── migrations/                # 数据库迁移
├── api/openapi.yaml           # API 文档片段
└── README.md
```

## 分支命名

```
feature/M03-device-registration
feature/M07-software-blacklist
```

## 提交信息格式

```
feat(device): add agent registration endpoint
fix(audit): correct ClickHouse batch insert
docs(architecture): update NAC integration diagram
```

## 服务间通信规则

1. **同步查询**：gRPC（定义在 `proto/`）
2. **异步事件**：NATS JetStream，主题格式 `sentinel.{domain}.{action}.{tenant_id}`
3. **禁止**：直接连接其他服务的数据库

## Agent Enforcer 开发

管控类模块需同时实现：

1. 云端 `services/{module}/` — 策略管理与事件处理
2. Agent `agent/enforcers/{module}/` — 本地策略执行
3. 策略 DSL 文档 — 在模块 README 中说明
