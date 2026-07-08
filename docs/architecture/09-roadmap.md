# 分期建设路线图

## 1. 总览

```
P0 基础平台 ──► P1 管控增强 ──► P2 安全纵深 ──► P3 高级能力 ──► P4 智能化
  (8周)           (6周)           (8周)           (10周)          (持续)
```

时间供沟通参考，实施以模块依赖与验收标准为准。

## 2. P0 — 基础平台（MVP）

**目标**：完成 客户端纳管、资产可见、审计可查、控制台可用。

### 交付模块

| 模块 | 交付物 |
|------|--------|
| gateway | 路由、JWT 校验、健康检查 |
| identity | 租户/用户/RBAC、登录 API |
| device | 注册、心跳、设备 CRUD、分组 |
| asset | 硬件/软件清单采集与查询 |
| audit | 事件消费、ClickHouse 写入、查询 API |
| agent | core + transport + collectors(asset) |
| console | 登录、设备列表、资产详情、审计查询 |

### 验收标准

- [ ] 500 台 客户端同时在线，心跳延迟 P99 &lt; 2s
- [ ] 新设备 5 分钟内完成资产首报
- [ ] 管理员操作均有审计记录
- [ ] docker-compose 一键启动全栈

## 3. P1 — 管控增强

**目标**：统一策略下发，软件管控与合规检查上线。

### 交付模块

| 模块 | 交付物 |
|------|--------|
| policy | 策略 CRUD、发布、生效计算、客户端策略包 |
| software | 黑白名单、违规检测与告警 |
| compliance | 基线库、扫描任务、合规分数 |
| agent | policy engine + software enforcer + compliance collector |
| console | 策略编辑器、合规仪表盘 |

### 验收标准

- [ ] 策略发布后 3 分钟内 客户端生效
- [ ] 黑名单软件启动被拦截并产生审计
- [ ] 合规分数可展示并按组织筛选

## 4. P2 — 安全纵深

**目标**：入网准入与数据防泄漏。

### 交付模块

| 模块 | 交付物 |
|------|--------|
| dlp | 规则引擎、USB/文件外发管控、事件取证 |
| nac | 准入策略、合规联动、RADIUS 集成模板 |
| agent | dlp enforcer、nac enforcer |
| console | DLP 事件处置、准入状态视图 |

### 验收标准

- [ ] 不合规设备无法访问生产网段（测试环境验证）
- [ ] 敏感文件拷贝至 USB 被阻断并留存取证
- [ ] DLP 事件可在审计中检索

## 5. P3 — 高级能力

**目标**：零信任、移动端、远程运维。

### 交付模块

| 模块 | 交付物 |
|------|--------|
| zerotrust | 信任分、应用访问策略 |
| mdm | iOS/Android 基础纳管 |
| remote | 远程桌面、会话审计 |
| console | 对应管理页面 |

### 验收标准

- [ ] 低信任分设备无法访问指定内网应用
- [ ] 远程会话全程录制并可审计回放
- [ ] MDM 可推送 Wi-Fi/VPN 配置（至少 Android）

## 6. P4 — AI 安全能力

**目标**：智能化运营辅助。

### 交付模块

| 模块 | 交付物 |
|------|--------|
| ai | 异常行为检测 API、NL 查询 PoC |
| console | 智能助手侧边栏 |

### 验收标准

- [ ] 异常登录/批量下载等行为自动告警
- [ ] 支持自然语言查询审计日志（中文）

## 7. 模块开发顺序（依赖排序）

```
1. pkg (公共库)
2. proto (接口定义)
3. identity → gateway
4. device → agent(core)
5. asset → audit
6. policy
7. software, compliance (可并行)
8. dlp, nac (可并行，依赖 compliance 分数)
9. zerotrust (依赖 nac + compliance)
10. mdm, remote (可并行)
11. ai
```

## 8. 分支策略

- `main`：稳定发布
- `develop`：集成开发
- `feature/M{xx}-{module-name}`：模块功能分支
- `cursor/*`：自动化开发分支

每个模块合并前需：单元测试、API 文档更新、迁移脚本、CHANGELOG 条目。
