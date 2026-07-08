# 模块开发指南

## 架构原则

- **一个 API 服务**：`backend/server`，不拆微服务
- **业务按包拆分**：`module.{name}` 实现业务逻辑
- **三端 API 分层**：`api.admin` / `api.app` / `api.client`

## 新模块 Checklist

- [ ] 在 `module/{name}/` 实现 Service、Repository、domain
- [ ] 在 `api.admin` 添加管理端 Controller（如需要）
- [ ] 在 `api.app` 添加移动端 Controller（如需要）
- [ ] 在 `api.client` 添加终端 Controller（如需要）
- [ ] 编写 Flyway 迁移 `server/src/main/resources/db/migration/`
- [ ] 写操作调用 `module.audit` 记录审计

## 目录结构

```
backend/server/src/main/java/com/sentinelhub/
├── api/
│   ├── admin/          # PC 管理控制台 API
│   ├── app/            # 手机 App API
│   └── client/          # PC 安全客户端 API
└── module/
    └── {name}/
        ├── XxxService.java
        ├── XxxRepository.java
        └── domain/
```

## 本地运行

```bash
cd backend
./mvnw -pl server spring-boot:run
```

## 调用规则

1. `api.*` → `module.*` Service，禁止 Controller 直连 Repository
2. `module.*` 之间通过 Service 接口调用，禁止跨包访问 Repository
3. 禁止 `api.admin` 与 `api.client` 互相调用
