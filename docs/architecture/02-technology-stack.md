# 技术选型

## 1. 选型原则

- **私有化友好**：核心组件可离线部署，不依赖公有云专有服务
- **跨平台**：PC 客户端需覆盖 Windows / macOS / Linux
- **可维护**：后端 Java；客户端 Flutter 统一手机+PC；PC 后台服务用 Go

## 2. 技术栈总览

```
┌─────────────┬──────────────────────────────────────────────────┐
│ 层级         │ 选型                                              │
├─────────────┼──────────────────────────────────────────────────┤
│ 管理控制台   │ React 18, TypeScript, Vite, Ant Design, Zustand  │
│ 统一 API 服务 │ Spring Boot 3.3 单体 + 业务模块分包 (Java 21)        │
│ API 通道     │ admin / app / client 三端 REST API                 │
│ 业务模块     │ Spring 包结构 module.* (非独立微服务)                  │
│ 统一客户端   │ Flutter 3.24（手机 iOS/Android + PC 桌面）            │
│ PC 后台服务  │ Go 1.22+（仅桌面端常驻）                               │
│ 关系型数据库 │ MySQL 8.4                                          │
│ 缓存         │ Redis 7                                            │
│ 消息队列     │ NATS JetStream                                     │
│ 日志/遥测库  │ ClickHouse                                         │
│ 对象存储     │ MinIO (S3 兼容)                                     │
│ 身份认证     │ Spring Security + OAuth2/OIDC (Keycloak 可插拔)      │
│ 可观测       │ Micrometer, Prometheus, Grafana, Loki              │
│ 容器编排     │ Docker, Kubernetes (Helm)                          │
│ 构建工具     │ Maven 3.9, Spring Boot BOM                         │
│ CI/CD        │ GitHub Actions                                     │
└─────────────┴──────────────────────────────────────────────────┘
```

## 3. 关键选型说明

### 3.1 后端：Java 21 + Spring Boot 3

- **Spring Boot 3.3**：成熟的企业级框架，生态完善，适合中大型安全平台
- **Java 21**：LTS 版本，虚拟线程（Project Loom）适合高并发网关与事件消费
- **Maven 多模块**：`backend/` 下按域拆分模块，父 POM 统一依赖版本管理
- **MyBatis-Plus**：灵活 SQL 控制，适合复杂查询（设备、审计、资产）
- **Spring Security**：RBAC、OAuth2、方法级权限

### 3.2 后端模块结构

```
backend/
├── pom.xml              # 父 POM
├── common/              # 公共库
└── server/              # 统一 API 服务（唯一进程 :8080）
    └── src/main/java/com/sentinelhub/
        ├── api/
        │   ├── admin/   # 管理端 API — PC Web 控制台
        │   ├── app/     # 移动端 API — 手机 App
        │   └── client/   # 终端 API — PC 安全客户端
        └── module/
            ├── identity/
            ├── device/
            ├── asset/
            └── ...      # 按业务域分包，非独立微服务
```

### 3.3 通信协议

| 场景 | 协议 | 说明 |
|------|------|------|
| 管理控制台 | HTTPS `/api/admin/v1` | PC 浏览器 |
| 手机 App | HTTPS `/api/app/v1` | iOS / Android |
| PC 安全客户端 | HTTPS `/api/client/v1` + mTLS | Windows / macOS / Linux |
| 模块间调用 | Spring Bean 注入 | 同进程，无网络开销 |
| 异步事件 | NATS JetStream（可选） | 审计、告警通知 |

### 3.4 存储分工

| 存储 | 用途 | Java 访问层 |
|------|------|-------------|
| MySQL | 租户、用户、设备、策略 | MyBatis-Plus + HikariCP |
| Redis | 会话、在线状态、策略缓存 | Spring Data Redis / Redisson |
| ClickHouse | 审计日志、遥测 | clickhouse-jdbc 批量写入 |
| MinIO | 安装包、取证文件 | AWS S3 SDK |

### 3.5 客户端

三类客户端技术栈详见 [10-client-technology-stack.md](./10-client-technology-stack.md)。

| 客户端 | 目录 | 技术 |
|--------|------|------|
| 管理控制台（PC Web） | `console/` | React + TypeScript + Ant Design |
| **手机 + PC 客户端** | `client/` | **Flutter**（Dart） |
| PC 后台服务（仅桌面） | `client/service/` | Go |

### 3.6 统一客户端架构（Flutter）

手机与 PC **共用** `client/lib/`，编译到不同平台；PC 桌面另含 Go 后台服务：

```
client/
├── lib/                # Flutter UI（iOS/Android/Win/Mac/Linux 共享）
├── android/ ios/       # 移动端工程
├── windows/ macos/ linux/
└── service/            # PC 专用 Go 后台服务
```

详见 [10-client-technology-stack.md](./10-client-technology-stack.md)。

## 4. 核心依赖版本

| 依赖 | 版本 |
|------|------|
| Flutter | 3.24+ |
| Dart | 3.5+ |
| Go (PC 后台服务) | 1.22+ |
| React (管理控制台) | 18.x |
| Java | 21 (LTS) |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.x (按需引入) |
| MyBatis-Plus | 3.5.x |
| MySQL Connector/J | 8.3.x |
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

- **构建**：Maven Wrapper (`backend/mvnw`)
- **API 文档**：springdoc-openapi (Swagger UI)
- **Proto 生成**：protobuf-maven-plugin
- **代码规范**：Checkstyle, SpotBugs, ESLint (前端)
- **数据库迁移**：Flyway
- **本地开发**：docker-compose + `mvn -pl server spring-boot:run`
