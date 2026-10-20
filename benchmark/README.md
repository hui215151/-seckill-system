# 压测说明与实测结果

压测器是 `benchmark/LoadTest.java`，JDK 11 单文件，不依赖 JMeter/wrk。
它按固定并发持续压 `POST /api/seckill/do`，每个请求使用不同 `userId`，统计成功、业务拒绝（售罄/限流）、错误、TPS 与 P50/P95/P99。

## 1. 前置条件

- 应用、MySQL、Redis、RabbitMQ 均已启动（见根目录 README 或本地启动脚本）；
- 目标商品存在且处于活动时间内；
- 秒杀接口需要 JWT：压测器在本地用 HS256 直接签发 token（每个请求一个 `userId`），**secret 必须与服务端 `seckill.jwt.secret` 一致**，默认内置开发密钥；如服务端改过，用第 6 个参数传入；
- **每轮压测前必须清空队列、Redis 和上一轮订单**，否则会测到上一轮的残留状态。

## 2. 重置环境

以下命令以 MySQL 在 `127.0.0.1:3307`、库 `seckill`、用户 `root` 为例，密码用环境变量传入，避免出现在命令行历史里。

```bash
# 1) 清空 MQ 队列（关键：否则上一轮的孤儿消息会继续建单）
rabbitmqctl purge_queue seckill.order.queue
rabbitmqctl purge_queue seckill.order.retry
rabbitmqctl purge_queue seckill.order.dlq

# 2) 重置库存与订单（stock 按测试目标调整）
mysql -h127.0.0.1 -P3307 -uroot -p seckill -e "
  truncate table seckill_order;
  truncate table transaction_log;
  update seckill_goods set stock_count=500, version=0 where id=1;"

# 3) 清空 Redis（本项目专用实例，直接 FLUSHDB；不要用 --scan 逐 key 删，几千个 key 会非常慢）
redis-cli -p 6379 flushdb
```

> 重置后必须**重启应用**，让 `initStockToRedis()` 按数据库库存重新预热。

## 3. 容量测试（测吞吐上限）

为了不因库存耗尽提前进入“售罄”分支，先把库存调大、并把限流临时放开：

```bash
# 库存调大
mysql -h127.0.0.1 -P3307 -uroot -p seckill -e "update seckill_goods set stock_count=200000 where id=1;"

# 重启应用并放开限流（默认 1000 QPS）
JAVA_OPTS="-Dseckill.rate-limit.qps=100000" ./start-all.sh   # Windows: $env:JAVA_OPTS='-Dseckill.rate-limit.qps=100000'

# 200 并发，持续 15 秒
java benchmark/LoadTest.java http://localhost:8080 1 200 15
```

## 4. 超卖验证（测正确性）

库存 500，50 并发持续 10 秒，请求数远大于库存：

```bash
# 恢复库存 500，按第 2 节重置并重启
java benchmark/LoadTest.java http://localhost:8080 1 50 10
```

压测结束后等待 MQ 消费完（约 5–10 秒），用 SQL 校验：

```sql
select stock_count, version from seckill_goods where id=1;   -- 期望 0 / 500
select count(*) from seckill_order where goods_id=1;         -- 期望 500
select status, count(*) from transaction_log group by status;-- 期望 1 -> 500
select count(*) from seckill_order;                          -- 期望 500（没有多建）
```

## 5. 实测结果

> 测试时间：2026-10-20
> 机器：Windows 11 / Intel i5-13500HX（14C20T）/ 15.7 GB RAM
> 组件同机部署：MySQL 8.0.43（3307）、Redis 7.4.11（6379）、RabbitMQ 3.13.7（5672）
> JVM：JDK 11，默认堆；Spring Boot 2.7.14

### 容量测试

| 并发 | 时长 | 总请求 | 成功 | TPS | P50 | P95 | P99 |
|---|---|---|---|---|---|---|---|
| 200 | 15.2s | 5232 | 5232 | **343** | 528ms | 707ms | 1504ms |

### 超卖验证

| 并发 | 时长 | 总请求 | 成功 | 业务拒绝 | 错误 | TPS | P50 | P95 |
|---|---|---|---|---|---|---|---|---|
| 50 | 10.0s | 4318 | **500** | 3818 | 0 | 431 | 98ms | 192ms |

校验结果：`stock_count=0`、`orders=500`、`transaction_log status=1` 共 500 条、Redis 库存 0 —— **零超卖、零重复订单**。

### 结论与边界

- 本机容量约 **340–360 TPS**，瓶颈在“每请求一次商品主键查询 + 事务表写入 + MQ 发布 + JWT 校验”这条同步链路，不是 Redis 扣减；
- 超卖测试中的 431 TPS 包含大量快速失败的“售罄”请求，**不代表系统容量**；
- 测试期间 MySQL 设置了 `innodb_flush_log_at_trx_commit=2`、`sync_binlog=0`（本地压测的耐久性取舍），生产环境不应照搬；
- 所有组件在同一台笔记本上，结果不能与生产集群对比，仅用于验证方案正确性与本机容量。

## 6. 已知现象

- 压测器默认用户 ID 基于时间戳生成，避免复用上一轮用户被防重拦截；如需固定用户段，传第 5 个参数；
- `LoadTest` 会在每个请求上附 `Authorization: Bearer <token>`，token 由 `userId` 现场签发，因此不需要真的登录；前提是 secret 与服务端一致；
- 业务拒绝包含“售罄”“活动太火爆（限流）”“请勿重复秒杀”，脚本统一计入 `rejected`；
- 队列里残留的旧消息会被消费端的幂等校验跳过（事务表不存在或已终态），不会再凭库存建单。
