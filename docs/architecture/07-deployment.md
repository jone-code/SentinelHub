# 部署架构

## 1. 环境划分

| 环境 | 用途 | 部署方式 |
|------|------|----------|
| dev | 本地开发 | docker-compose |
| staging | 集成测试 | K8s 单集群 |
| prod | 生产 | K8s 高可用集群 |

## 2. Docker Compose（开发/小规模）

文件：`deploy/docker-compose/docker-compose.yml`

```yaml
# 服务组件
services:
  - postgres
  - redis
  - nats
  - clickhouse
  - minio
  - sentinel-server    # 统一 API 服务
  - console (nginx 静态)
```

启动：

```bash
make dev-up           # 启动基础设施
make backend-run      # 启动 API 服务
```

端口规划：

| 服务 | 端口 |
|------|------|
| sentinel-server | 8080 |
| Console | 3000 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| NATS | 4222 |
| ClickHouse HTTP | 8123 |
| MinIO | 9000 |

## 3. Kubernetes 部署

### 3.1 Helm Chart 结构

```
deploy/helm/sentinelhub/
├── Chart.yaml
├── values.yaml
├── values-prod.yaml
└── templates/
    ├── gateway/
    ├── identity/
    ├── device/
    ├── ...
    ├── ingress.yaml
    └── secrets.yaml
```

### 3.2 命名空间

```
sentinelhub-system    # 平台服务
sentinelhub-data      # 有状态组件（可选独立）
```

### 3.3 Ingress

```yaml
# 控制台
console.sentinel.example.com → console

# API
api.sentinel.example.com → gateway

# Agent 接入（可独立域名）
agent.sentinel.example.com → gateway (/agent/*)
```

### 3.4 资源建议（生产最小）

| 组件 | replicas | CPU | Memory |
|------|----------|-----|--------|
| gateway | 2 | 500m | 512Mi |
| identity | 2 | 250m | 256Mi |
| device | 2 | 500m | 512Mi |
| policy | 2 | 500m | 512Mi |
| audit | 2 | 500m | 1Gi |
| 其他业务服务 | 1-2 | 250m | 256Mi |

## 4. 有状态组件

### PostgreSQL

- 生产：云 RDS 或 Patroni 主从
- 连接池：PgBouncer（sidecar 或独立）
- 备份：每日全量 + WAL 归档

### ClickHouse

- 审计写入：`ReplicatedMergeTree`
- 查询：只读副本分担报表

### NATS JetStream

- 3 节点集群，R1/R3 副本按主题配置
- 持久化卷 50GB+

### MinIO

- 分布式模式 4+ 节点，或单节点 + 外部备份

## 5. Agent 分发

```
https://agent.sentinel.example.com/download/
  ├── windows/amd64/SentinelAgent-setup.exe
  ├── darwin/universal/SentinelAgent.pkg
  └── linux/amd64/sentinel-agent.deb
```

- 安装包托管于 MinIO，经 CDN/网关签名 URL 分发
- 版本清单：`/download/manifest.json`

## 6. 网络要求

### 出站（Agent → 平台）

| 目标 | 端口 | 协议 |
|------|------|------|
| agent.sentinel.example.com | 443 | HTTPS |

### 入站（管理员）

| 目标 | 端口 | 协议 |
|------|------|------|
| console.sentinel.example.com | 443 | HTTPS |
| api.sentinel.example.com | 443 | HTTPS |

### NAC 集成（可选）

| 目标 | 端口 | 协议 |
|------|------|------|
| RADIUS | 1812/1813 | UDP |
| 交换机 CoA | 3799 | UDP |

## 7. 密钥管理

- 生产使用 K8s Secrets + 外部 KMS（可选）
- 内部 CA 证书：`cert-manager` 自动轮换
- 数据库凭据：启动时从 Secret 注入，禁止写死在镜像

## 8. 监控告警

| 指标 | 告警阈值 |
|------|----------|
| Agent 在线率 | &lt; 95% 持续 15min |
| Gateway 5xx | &gt; 1% 持续 5min |
| NATS 消费延迟 | &gt; 60s |
| PG 连接数 | &gt; 80% max |
| ClickHouse 磁盘 | &gt; 85% |

Dashboard：`deploy/grafana/dashboards/`

## 9. 升级策略

- 平台服务：滚动更新，readiness 探针
- Agent：灰度通道（按 device_group 推送新版本）
- 数据库：migrate Job 在部署前执行（Helm pre-hook）
