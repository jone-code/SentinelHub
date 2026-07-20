# ClickHouse ReplacingMergeTree 在线迁移运维手册

## 背景

SentinelHub 审计与客户端事件冷存储使用 ClickHouse。Phase 10 起新表默认 `ReplacingMergeTree(created_at)`；存量 `MergeTree` 表需在线迁移以启用去重。

## 前置条件

| 变量 | 要求 |
|------|------|
| `AUDIT_CH_ENABLED` | `true` |
| `AUDIT_CH_REPLACING_MERGE` | `true` |
| `AUDIT_CH_REPLACING_MERGE_MIGRATE` | 启动自动迁移时设为 `true` |

## 迁移流程（每张表）

1. **检查引擎** — 查询 `system.tables`，已是 `ReplacingMergeTree` 则跳过
2. **创建 staging** — `audit_logs_new` / `client_events_new`
3. **分批复制** — `INSERT INTO ..._new SELECT * FROM ... LIMIT batch OFFSET offset`（默认 batch=10000）
4. **断点续传** — 进度写入 MySQL `clickhouse_migration_checkpoints`，失败后可从 offset 继续
5. **原子切换** — `RENAME TABLE` 交换新旧表
6. **清理** — `DROP` 旧表与 checkpoint

| 变量 | 默认 | 说明 |
|------|------|------|
| `AUDIT_CH_MIGRATION_BATCH_SIZE` | 10000 | 每批复制行数 |
| `AUDIT_CH_MIGRATION_RESUME` | true | 启用断点续传 |

迁移期间表短暂不可用（RENAME 瞬间）。建议在低峰期执行。

**Phase 14+**：`POST .../clickhouse-migration/run` 在后台线程执行并立即返回；可通过 GET 轮询进度。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/v1/platform/clickhouse-migration` | 查看迁移状态与各表步骤 |
| POST | `/api/admin/v1/platform/clickhouse-migration/run` | 手动触发迁移 |

### 状态字段

- `status`: `idle` / `running` / `completed` / `failed` / `skipped`
- `tables[].step`: `prepare` → `create_new` → `copy_data` → `rename` → `drop_old` → `done`

### 示例

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/admin/v1/platform/clickhouse-migration

curl -X POST -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/admin/v1/platform/clickhouse-migration/run
```

## 回滚

迁移成功后旧表已删除，**无法自动回滚**。回滚需从备份恢复或重新 `INSERT` 历史数据。

## 故障排查

| 现象 | 处理 |
|------|------|
| `migration already running` | 等待当前任务结束或重启服务 |
| `unexpected engine` | 手动检查 `system.tables`，非 MergeTree 族需人工处理 |
| HTTP 超时 | 大表复制耗时长，可调大服务端 HTTP 超时或分批迁移 |
| 迁移后查询仍重复 | 确认 `AUDIT_CH_REPLACING_MERGE=true`，查询使用 `FINAL` |

## 监控

`GET /api/admin/v1/platform/metrics-summary` 的 `clickhouse_migration` 字段包含实时进度，可接入 Grafana 自定义面板。
