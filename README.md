# 高并发秒杀系统

一个用于学习高并发场景的**本地演示项目**：Spring Boot + Redis + RabbitMQ + MySQL，核心解决秒杀中的**超卖、重复下单、数据库被打垮**三个问题。

> 说明：这是个人学习/演示项目，不是生产系统。所有性能数字都来自本机实测，复现方法见 [benchmark/README.md](benchmark/README.md)。

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 11（源码级别 1.8） |
| Spring Boot | 2.7.14 |
| MySQL | 8.x（验证用 8.0.43） |
| Redis | 7.4（验证用 7.4.11） |
| RabbitMQ | 3.13（Erlang 26） |
| MyBatis-Plus | 3.5.3.1 |
| Guava RateLimiter | 31.1-jre |

## 架构

```
                 ┌──────────────────────────────┐
   HTTP 请求 ───▶ │  SeckillController           │
                 │  ├─ Guava RateLimiter 限流    │
                 │  └─ 参数校验                 │
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │  SeckillServiceImpl          │
                 │  1. 商品/活动时间校验(MySQL)  │
                 │  2. SETNX 防重(Redis)        │
                 │  3. Lua 原子预扣库存(Redis)   │
                 │  4. 写本地事务表(MySQL)       │
                 │  5. 发送 MQ 消息             │
                 └──────────────┬───────────────┘
                                ▼
                    RabbitMQ: seckill.order.queue
                                ▼
                 ┌──────────────────────────────┐
                 │  SeckillOrderConsumer        │
                 │  幂等校验 + 手动 ACK          │
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │  createOrder                 │
                 │  MySQL 乐观锁扣减 + 建订单    │
                 └──────────────┬───────────────┘
                                ▼
                 TransactionCheckJob：扫描失败事务，回补 Redis 库存
```

## 核心设计

| 问题 | 方案 | 代码位置 |
|---|---|---|
| 超卖 | Lua 脚本原子预扣 Redis 库存 + MySQL 乐观锁兜底 | `lua/stock_deduct.lua`、`SeckillGoodsMapper#deductStock` |
| 重复下单 | Redis `SETNX` 防重，TTL 覆盖整个活动周期 | `SeckillServiceImpl#doSeckill` |
| 数据库被打垮 | 请求先扣 Redis，订单异步落库（MQ 削峰） | `RabbitMQConfig`、`SeckillOrderConsumer` |
| 消息重复/脏消息 | 消费前校验事务表状态，非「处理中」直接 ACK 跳过 | `SeckillOrderConsumer` |
| 消费失败 | 记录失败事务、`nack` 不重回队列，对账任务回补库存 | `TransactionCheckJob` |
| 流量削峰 | Guava `RateLimiter`，QPS 可配置 | `seckill.rate-limit.qps` |

## 实测结果

本机：Windows 11 / i5-13500HX（14C20T）/ 15.7 GB RAM，所有组件同机部署。

| 场景 | 并发 | 请求数 | 结果 |
|---|---|---|---|
| 容量测试（库存 20 万） | 200 | 5600 | **367 TPS**，P95 681ms，P99 1336ms |
| 超卖验证（库存 500） | 50 | 5462 | **成功 500 / 拒绝 4962 / 错误 0**，订单 500，库存 0，零超卖 |

完整方法、参数和 SQL 校验见 [benchmark/README.md](benchmark/README.md)。**这些数字仅代表本机单实例，不是生产指标。**

## 快速开始

### 方式一：Docker 起依赖（可选）

```bash
docker compose up -d
```

会启动 MySQL(3307)、Redis(6379)、RabbitMQ(5672/15672)，并自动导入 `sql/seckill.sql`。
数据库密码为 compose 里的 `seckill123`，记得同步改 `application.yml`。

### 方式二：使用本机已安装的中间件

1. 启动 MySQL / Redis / RabbitMQ；
2. 导入建表与演示数据（**会 DROP 并重建三张表**）：

```bash
mysql -h127.0.0.1 -P3307 -uroot -p < sql/seckill.sql
```

3. 修改 `src/main/resources/application.yml` 中的连接信息；
4. 打包并启动：

```bash
mvn -DskipTests package
java -jar target/seckill-system-1.0.0.jar
```

5. 打开 <http://localhost:8080/api/seckill/goods> 应能看到 12 个演示商品。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/seckill/goods` | 商品列表（含分类、库存、活动时间） |
| POST | `/api/seckill/do?userId=1&goodsId=1` | 执行秒杀，返回临时订单（异步落库） |
| GET | `/api/seckill/result?userId=1&goodsId=1` | 查询秒杀结果 |

示例：

```bash
curl http://localhost:8080/api/seckill/goods
curl -X POST "http://localhost:8080/api/seckill/do?userId=1001&goodsId=1"
curl "http://localhost:8080/api/seckill/result?userId=1001&goodsId=1"
```

错误场景会返回 `{"code":500,"message":"..."}`，包括：`活动未开始`、`活动已结束`、`商品已售罄`、`请勿重复秒杀`、`活动太火爆，请稍后重试`。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `seckill.rate-limit.qps` | 1000 | 令牌桶 QPS，可用 `-Dseckill.rate-limit.qps=...` 覆盖 |
| `spring.datasource.druid.max-active` | 50 | 连接池上限 |
| `spring.rabbitmq.listener.simple.prefetch` | 1 | 消费端预取，配合手动 ACK |

> YAML 中纯数字密码必须加引号（例如 `password: "012345"`），否则会被解析成八进制整数。

## 测试

```bash
mvn test
```

当前包含 `SeckillWindowTest`（8 个用例），覆盖活动时间边界和防重 key TTL。

## 目录结构

```
├─ benchmark/            # 压测器与实测说明
├─ docs/                 # 架构说明、踩坑记录
├─ sql/seckill.sql       # 建表 + 12 条演示数据（相对时间）
├─ src/main/java/com/seckill
│  ├─ config/            # Redis / RabbitMQ / 定时任务配置
│  ├─ controller/        # 接口与限流
│  ├─ service/impl/      # 秒杀核心逻辑、MQ 消费建单
│  ├─ mq/                # 消费者（幂等 + ACK）
│  ├─ job/               # 失败事务对账
│  └─ support/           # 活动时间窗口工具
└─ src/test/java         # 单元测试
```

## 已知限制

- **JWT 未实现**：`pom.xml` 引入了 `jjwt`，但没有任何鉴权过滤器，接口可直接调用；
- **单机单实例**：没有分布式锁、没有分库分表，Redis/MySQL/MQ 都是单点；
- **商品元信息每次请求查一次 MySQL**（主键查询），库存扣减才走 Redis；如需进一步压榨 QPS，可把活动时间缓存到 Redis；
- **启动会覆盖 Redis 库存**：`initStockToRedis()` 每次启动按数据库库存重置，重启前应确保队列已清空；
- **没有死信队列**：消费失败的消息直接丢弃，靠事务表 + 对账任务兜底；
- **无支付/关单流程**，订单状态仅用于演示。

## 相关文档

- [压测方法与实测结果](benchmark/README.md)
- [架构说明](docs/architecture.md)
- [踩坑记录](docs/踩坑记录.md)
