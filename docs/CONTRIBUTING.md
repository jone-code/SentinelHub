# 模块开发指南

## 新模块 Checklist

开发新功能模块前，请确认以下事项：

- [ ] 阅读 [04-module-design.md](../architecture/04-module-design.md) 中对应模块定义
- [ ] 在 `backend/{module}/` 创建或扩展 Spring Boot 模块
- [ ] 定义 `proto/{module}/v1/*.proto`（如需 gRPC）
- [ ] 编写 Flyway 迁移脚本 `src/main/resources/db/migration/`
- [ ] 实现 REST API（遵循 [06-api-design.md](../architecture/06-api-design.md)）
- [ ] 发布 NATS 事件，审计事件使用 `com.sentinelhub.common.audit.AuditEvent`
- [ ] 更新 `console/src/pages/{module}/`
- [ ] 补充单元测试与 README

## 服务目录结构

```
backend/{module}/
├── build.gradle.kts
├── src/main/java/com/sentinelhub/{module}/
│   ├── {Module}Application.java    # Spring Boot 入口
│   ├── config/                     # 配置类
│   ├── controller/                 # REST 控制器
│   ├── service/                    # 业务逻辑
│   ├── repository/                 # 数据访问 (MyBatis-Plus Mapper)
│   └── domain/                     # 实体与 DTO
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/               # Flyway SQL
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

1. **同步查询**：gRPC 或 OpenFeign（定义在 `proto/` 或 Feign Client 接口）
2. **异步事件**：NATS JetStream，主题格式 `sentinel.{domain}.{action}.{tenant_id}`
3. **禁止**：直接连接其他服务的数据库

## Agent Enforcer 开发

管控类模块需同时实现：

1. 云端 `backend/{module}/` — 策略管理与事件处理（Java）
2. Agent `agent/enforcers/{module}/` — 本地策略执行（Go）
3. 策略 DSL 文档 — 在模块 README 中说明

## 本地运行单个服务

```bash
cd backend
./gradlew :gateway:bootRun
./gradlew :device:bootRun
```
