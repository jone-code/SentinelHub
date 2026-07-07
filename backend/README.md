# SentinelHub Backend

Java 21 + Spring Boot 3.3 多模块后端。

## 模块列表

| 模块 | 端口 | 说明 |
|------|------|------|
| gateway | 8080 | API 网关 |
| identity | 8081 | 身份与租户 |
| device | 8082 | 设备管控 |
| asset | 8083 | 资产管理 |
| audit | 8084 | 审计日志 |
| policy | 8085 | 策略引擎 |
| software | 8086 | 软件管控 |
| compliance | 8087 | 合规检查 |
| dlp | 8088 | 数据防泄漏 |
| nac | 8089 | 终端准入 |
| zerotrust | 8090 | 零信任 |
| mdm | 8091 | MDM |
| remote | 8092 | 远程控制 |
| ai | 8093 | AI 安全（预留） |

## 构建

```bash
./gradlew build
```

## 运行

```bash
# 启动单个服务
./gradlew :gateway:bootRun
./gradlew :device:bootRun

# 健康检查
curl http://localhost:8080/health
```

## 公共库

`common` 模块提供：
- `ApiResponse` / `PageResponse` — 统一 API 响应
- `TenantContext` — 多租户上下文
- `AuditEvent` — 审计事件模型
- `HealthController` — 健康检查端点
