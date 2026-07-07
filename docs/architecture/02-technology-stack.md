# 技术选型

## 1. 选型原则

- **私有化友好**：核心组件可离线部署，不依赖公有云专有服务
- **跨平台**：Agent 需覆盖 Windows / macOS / Linux
- **高性能**：终端规模万级时，心跳、日志、策略下发需低延迟
- **可维护**：后端统一 Java 生态，便于企业团队招聘与运维；Agent 使用 Go 保证轻量跨平台

## 2. 技术栈总览

```
┌─────────────┬──────────────────────────────────────────────────┐
│ 层级         │ 选型                                              │
├─────────────┼──────────────────────────────────────────────────┤
│ 管理控制台   │ React 18, TypeScript, Vite, Ant Design, Zustand  │
│ API 网关     │ Spring Cloud Gateway / Spring Boot (Java 21)       │
│ 业务微服务   │ Spring Boot 3.3, Spring Security, MyBatis-Plus   │
│ 服务间通信   │ gRPC (protobuf) + OpenFeign (REST 内部调用)         │
│ 终端 Agent   │ Go 1.22+（跨平台、低资源占用）                       │
│ 关系型数据库 │ PostgreSQL 16                                      │
│ 缓存         │ Redis 7                                            │
│ 消息队列     │ NATS JetStream                                     │
│ 日志/遥测库  │ ClickHouse                                         │
│ 对象存储     │ MinIO (S3 兼容)                                     │
│ 身份认证     │ Spring Security + OAuth2/OIDC (Keycloak 可插拔)      │
│ 可观测       │ Micrometer, Prometheus, Grafana, Loki              │
│ 容器编排     │ Docker, Kubernetes (Helm)                          │
│ 构建工具     │ Gradle 8 (Kotlin DSL), Maven BOM                   │
│ CI/CD        │ GitHub Actions                                     │
└─────────────┴──────────────────────────────────────────────────┘
```

## 3. 关键选型说明

### 3.1 后端：Java 21 + Spring Boot 3

- **Spring Boot 3.3**：成熟的企业级框架，生态完善，适合中大型安全平台
- **Java 21**：LTS 版本，虚拟线程（Project Loom）适合高并发网关与事件消费
- **Gradle 多模块**：`backend/` 下按域拆分模块，统一依赖版本管理
- **MyBatis-Plus**：灵活 SQL 控制，适合复杂查询（设备、审计、资产）
- **Spring Security**：RBAC、OAuth2、方法级权限

### 3.2 后端模块结构

```
backend/
├── build.gradle.kts          # 根构建脚本
├── settings.gradle.kts       # 模块声明
├── common/                   # 公共 DTO、审计模型、租户上下文
├── gateway/                  # API 网关 (:8080)
├── identity/                 # 身份租户 (:8081)
├── device/                   # 设备管控 (:8082)
├── asset/                    # 资产管理 (:8083)
├── audit/                    # 审计日志 (:8084)
├── policy/                   # 策略引擎 (:8085)
├── software/                 # 软件管控 (:8086)
├── compliance/               # 合规检查 (:8087)
├── dlp/                      # DLP (:8088)
├── nac/                      # NAC (:8089)
├── zerotrust/                # 零信任 (:8090)
├── mdm/                      # MDM (:8091)
├── remote/                   # 远程控制 (:8092)
└── ai/                       # AI 安全 (:8093)
```

每个可执行模块均为独立 Spring Boot 应用，后期可按负载独立扩缩容，也可合并为单体启动（开发期）。

### 3.3 通信协议

| 场景 | 协议 | 说明 |
|------|------|------|
| 控制台 ↔ 网关 | HTTPS REST + WebSocket | OpenAPI 3 规范 |
| 网关 ↔ 微服务 | Spring Cloud Gateway 路由 / gRPC | 内网 mTLS |
| Agent ↔ 平台 | HTTPS + mTLS | REST 长轮询，大规模可升级 MQTT |
| 服务间异步 | NATS JetStream | 审计、资产变更、策略下发 |
| 内部 REST | OpenFeign | 同步查询类调用 |

### 3.4 存储分工

| 存储 | 用途 | Java 访问层 |
|------|------|-------------|
| PostgreSQL | 租户、用户、设备、策略 | MyBatis-Plus + HikariCP |
| Redis | 会话、在线状态、策略缓存 | Spring Data Redis / Redisson |
| ClickHouse | 审计日志、遥测 | clickhouse-jdbc 批量写入 |
| MinIO | 安装包、取证文件 | AWS S3 SDK |

### 3.5 前端

- **Ant Design Pro** 风格管理台
- **React Query** 管理服务端状态
- **ECharts** 合规与资产可视化

### 3.6 Agent：Go

终端 Agent 保持 **Go** 实现，原因：

- 单二进制跨平台分发，体积小、资源占用低
- 与安全管控类竞品（CrowdStrike、osquery 等）技术路线一致
- 通过 `proto/` 定义的 HTTP/gRPC 契约与 Java 后端通信，语言解耦

```
agent/
├── core/           # 生命周期、升级、配置拉取
├── transport/      # 与云端通信、mTLS
├── policy/         # 本地策略引擎
├── collectors/     # 资产、软件、合规采集
├── enforcers/      # DLP/NAC/软件管控执行插件
└── platform/       # windows/darwin/linux
```

## 4. 核心依赖版本

| 依赖 | 版本 |
|------|------|
| Java | 21 (LTS) |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.x (按需引入) |
| MyBatis-Plus | 3.5.x |
| PostgreSQL Driver | 42.7.x |
| gRPC Java | 1.65.x |
| Protobuf | 3.25.x |

## 5. 不采用的方案（及原因）

| 方案 | 原因 |
|------|------|
| Go 后端 | 用户明确要求 Java 技术栈 |
| Elasticsearch 作主库 | 运维成本高，审计场景 ClickHouse 更合适 |
| Kafka（初期） | 中小规模 NATS 更轻量 |
| JPA/Hibernate only | 复杂报表与审计查询需要灵活 SQL |

## 6. 开发工具链

- **构建**：Gradle Wrapper (`backend/gradlew`)
- **API 文档**：springdoc-openapi (Swagger UI)
- **Proto 生成**：protobuf-gradle-plugin
- **代码规范**：Checkstyle, SpotBugs, ESLint (前端)
- **数据库迁移**：Flyway
- **本地开发**：docker-compose + `./gradlew :gateway:bootRun`
