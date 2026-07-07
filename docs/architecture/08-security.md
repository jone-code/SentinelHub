# 安全架构

## 1. 威胁模型概要

| 威胁 | 缓解措施 |
|------|----------|
| 未授权访问控制台 | OIDC/MFA、RBAC、会话超时 |
| Agent 伪造 | mTLS 双向认证、设备指纹绑定 |
| 租户数据越权 | 网关强制 tenant_id、行级隔离 |
| 审计篡改 | ClickHouse 追加写、对象存储 WORM（可选） |
| 中间人攻击 | TLS 1.2+、证书固定（Agent 可选） |
| DLP 绕过 | 内核级/驱动级钩子（分阶段）、本地策略缓存加密 |

## 2. 身份与访问控制

### RBAC 权限模型

```
权限格式：{module}:{resource}:{action}
示例：
  device:device:read
  policy:policy:write
  audit:log:export
  dlp:rule:write
```

### 预置角色

| 角色 | 权限范围 |
|------|----------|
| super_admin | 租户内全部 |
| security_admin | 策略、DLP、NAC、合规 |
| it_admin | 设备、资产、软件、远程 |
| auditor | 审计只读 |
| viewer | 仪表盘只读 |

## 3. 传输安全

- 外部：TLS 1.2+，推荐 TLS 1.3
- 服务间：mTLS（Istio 或自签 CA）
- Agent：注册后强制 mTLS，证书有效期 90 天自动续期

## 4. 数据安全

| 数据类型 | 措施 |
|----------|------|
| 用户密码 | 不存储（OIDC）或 Argon2id |
| API Key | 仅存 hash |
| 审计日志 | 敏感字段脱敏（IP 部分掩码、文件路径哈希） |
| DLP 取证 | MinIO SSE-S3 加密 |
| 策略内容 | 传输加密，静态 PG 透明加密（可选） |

## 5. Agent 安全

- 安装包代码签名（Windows Authenticode / macOS Notarization）
- 本地配置与策略缓存 AES-256 加密
- 防卸载：需管理员密码或服务端授权（可配置）
- 自保护：watchdog 进程、完整性校验

## 6. 合规对齐

平台设计参考（非认证承诺）：

- **等保 2.0**：身份鉴别、访问控制、安全审计、入侵防范
- **GDPR/个保法**：数据最小化、导出与删除 API
- **SOC 2**：审计完整性、变更管理流程

## 7. 安全开发生命周期

- 依赖漏洞扫描：Dependabot / trivy 镜像扫描
- SAST：SpotBugs, semgrep, ESLint security
- 密钥：禁止提交 `.env` 与私钥，gitleaks CI
- 渗透测试：Major 版本发布前

## 8. 事件响应

安全事件统一进入 Audit + 告警中心：

- `severity: critical` → Webhook / 邮件 / 短信
- 支持自动隔离：联动 NAC 将设备移入隔离 VLAN
