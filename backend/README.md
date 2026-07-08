# SentinelHub Backend

Java 21 + Spring Boot 3.3，**单体 API 服务 + 业务模块分包**架构。

## 架构说明

```
backend/
├── common/          # 公共库（DTO、审计模型、工具类）
└── server/          # 统一 API 服务（唯一可执行模块）
    └── src/main/java/com/sentinelhub/
        ├── api/
        │   ├── admin/     # 管理端 API（Web 控制台）
        │   ├── app/       # 移动端 API（手机 App）
        │   └── client/     # 终端 API（PC 安全客户端）
        └── module/
            ├── identity/  # 身份租户
            ├── device/    # 设备管控
            ├── asset/     # 资产管理
            └── ...        # 其他业务模块
```

**设计原则**：按业务域分包，不按微服务拆进程。对外只有一个 API 服务（`:8080`）。

## API 端点分层

| 客户端 | 路径前缀 | 说明 |
|--------|----------|------|
| 管理端（PC 浏览器） | `/api/admin/v1` | Web 控制台 |
| 移动端（手机 App） | `/api/app/v1` | iOS / Android 管理 App |
| 终端（PC 安全客户端） | `/api/client/v1` | Windows / macOS / Linux 客户端 |

## 构建与运行

```bash
./mvnw clean package -DskipTests
./mvnw -pl server spring-boot:run
```

需先启动 MySQL（`make dev-up`），默认连接 `localhost:3306/sentinelhub`。

## 健康检查

```bash
curl http://localhost:8080/health
curl http://localhost:8080/api/admin/v1/info
curl http://localhost:8080/api/app/v1/info
curl http://localhost:8080/api/client/v1/info
```

## 业务模块

| 包路径 | 能力 |
|--------|------|
| `module.identity` | 租户、用户、RBAC |
| `module.device` | 设备注册、心跳、分组 |
| `module.asset` | 软硬件资产 |
| `module.audit` | 审计日志 |
| `module.policy` | 策略引擎 |
| `module.software` | 软件管控 |
| `module.compliance` | 合规检查 |
| `module.dlp` | 数据防泄漏 |
| `module.nac` | 终端准入 |
| `module.zerotrust` | 零信任 |
| `module.mdm` | 移动设备管理 |
| `module.remote` | 远程控制 |
| `module.ai` | AI 安全（预留） |

API 层（`api.admin` / `api.app` / `api.client`）负责协议适配，调用 `module.*` 业务服务。
