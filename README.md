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
| JWT | jjwt 0.11.5（HS256） |
| 密码哈希 | Spring Security Crypto（BCrypt） |
| Guava RateLimiter | 31.1-jre |

## 架构

```
                 ┌──────────────────────────────┐
   HTTP 请求 ───▶ │  /api/auth/login 签发 JWT     │
                 └──────────────┬───────────────┘
                                │ Bearer token
                                ▼
                 ┌──────────────────────────────┐
                 │  JwtAuthInterceptor          │
                 │  校验签名/过期，注入 userId   │
                 └──────────────┬───────────────┘
                                ▼
                 ┌──────────────────────────────┐
                 │  SeckillController           │
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
                                │
                                ▼
                 ┌──────────────────────────────┐
                 │  SeckillOrderConsumer        │
                 │  幂等校验 + 手动 ACK          │
                 └───────┬──────────────┬───────┘
                    成功 │              │ 失败（nack, requeue=false）
                         ▼              ▼
                 ┌──────────────┐   retry queue（TTL 5s）
                 │ createOrder  │       │ 到期重回主队列
                 │ MySQL 乐观锁 │       │ 重试 3 次仍失败
                 │ 扣减 + 建单  │       ▼
                 └──────┬───────┘   seckill.order.dlq（人工排查）
                        ▼
                 TransactionCheckJob：扫描失败事务，回补 Redis 库存
```

## 核心设计

| 问题 | 方案 | 代码位置 |
|---|---|---|
| 超卖 | Lua 脚本原子预扣 Redis 库存 + MySQL 乐观锁兜底 | `lua/stock_deduct.lua`、`SeckillGoodsMapper#deductStock` |
| 重复下单 | Redis `SETNX` 防重，TTL 覆盖整个活动周期 | `SeckillServiceImpl#doSeckill` |
| 数据库被打垮 | 请求先扣 Redis，订单异步落库（MQ 削峰） | `RabbitMQConfig`、`SeckillOrderConsumer` |
| 消息重复/脏消息 | 消费前校验事务表状态，非「待处理/待重试」直接 ACK 跳过 | `SeckillOrderConsumer` |
| 消费失败 | 重试队列（5s TTL）重试 3 次，仍失败转入死信队列；失败事务由对账任务回补库存 | `RabbitMQConfig`、`SeckillOrderConsumer` |
| 流量削峰 | Guava `RateLimiter`，QPS 可配置 | `seckill.rate-limit.qps` |
| 身份伪造 | 登录签发 JWT（HS256），拦截器校验后把 `userId` 注入请求；不再信任前端传的 userId | `AuthService`、`JwtAuthInterceptor` |

## 实测结果

本机：Windows 11 / i5-13500HX（14C20T）/ 15.7 GB RAM，所有组件同机部署。

| 场景 | 并发 | 请求数 | 结果 |
|---|---|---|---|
| 容量测试（库存 20 万） | 200 | 5232 | **343 TPS**，P95 707ms，P99 1504ms |
| 超卖验证（库存 500） | 50 | 4318 | **成功 500 / 拒绝 3818 / 错误 0**，订单 500，库存 0，零超卖 |

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
2. 导入建表与演示数据（**会 DROP 并重建四张表**）：

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

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 无 | 登录换取 JWT |
| GET | `/api/seckill/goods` | 无 | 商品列表（分类、库存、活动时间） |
| POST | `/api/seckill/do?goodsId=1` | Bearer | 执行秒杀，返回临时订单（异步落库） |
| GET | `/api/seckill/result?goodsId=1` | Bearer | 查询当前登录用户的秒杀结果 |

演示账号（`sql/seckill.sql`）：`alice` / `bob` / `carol`，密码统一 `123456`。

```bash
# 1. 登录拿 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"123456"}'
# {"token":"eyJ...","userId":1,"username":"alice","expiresIn":7200}

# 2. 带 token 下单
curl -X POST "http://localhost:8080/api/seckill/do?goodsId=1" \
  -H "Authorization: Bearer <token>"

# 3. 查询结果
curl "http://localhost:8080/api/seckill/result?goodsId=1" \
  -H "Authorization: Bearer <token>"
```

`userId` 不再由前端传入，统一从 token 的 `userId` claim 取，避免越权替别人下单。
未带 token / token 过期 / 签名错误返回 **HTTP 401**。

错误场景返回 `{"code":500,"message":"..."}`，包括：`活动未开始`、`活动已结束`、`商品已售罄`、`请勿重复秒杀`、`活动太火爆，请稍后重试`。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `seckill.rate-limit.qps` | 1000 | 令牌桶 QPS，可用 `-Dseckill.rate-limit.qps=...` 覆盖 |
| `seckill.jwt.secret` | 内置开发密钥 | HS256 密钥，**生产必须改成 32 字节以上的随机值** |
| `seckill.jwt.expire-minutes` | 120 | token 有效期（分钟） |
| `seckill.mq.max-retries` | 3 | 消费失败最大重试次数（不含首次） |
| `seckill.mq.retry-ttl-ms` | 5000 | 重试队列 TTL，到期后自动回主队列 |
| `spring.datasource.druid.max-active` | 50 | 连接池上限 |
| `spring.rabbitmq.listener.simple.prefetch` | 1 | 消费端预取，配合手动 ACK |

> YAML 中纯数字密码必须加引号（例如 `password: "012345"`），否则会被解析成八进制整数。

## 测试

```bash
mvn test
```

当前包含 `SeckillWindowTest`（8 个）、`SeckillRetryPolicyTest`（5 个）、`JwtUtilTest`（4 个），共 **17 个用例**，覆盖活动时间边界、防重 TTL、重试次数统计和 JWT 签发/校验。

> 升级提示：主队列新增了死信参数，若本地已存在旧队列，启动前先删除
> `seckill.order.queue`、`seckill.order.retry`、`seckill.order.dlq`，让应用重新声明拓扑。

## 目录结构

```
├─ benchmark/            # 压测器与实测说明
├─ docs/                 # 架构说明、踩坑记录
├─ sql/seckill.sql       # 建表 + 12 条演示商品 + 3 个演示账号（相对时间）
├─ src/main/java/com/seckill
│  ├─ config/            # Redis / RabbitMQ / MVC / 定时任务配置
│  ├─ controller/        # 登录、秒杀接口与限流
│  ├─ security/          # JWT 工具与登录拦截器
│  ├─ service/impl/      # 秒杀核心逻辑、MQ 消费建单
│  ├─ mq/                # 消费者（幂等 + ACK）
│  ├─ job/               # 失败事务对账
│  └─ support/           # 活动时间窗口工具
└─ src/test/java         # 单元测试
```

## 已知限制

- **JWT 无刷新/注销**：token 到期只能重新登录，没有 refresh token 和黑名单；密钥为单机配置，多实例需共享同一密钥；
- **单机单实例**：没有分布式锁、没有分库分表，Redis/MySQL/MQ 都是单点；
- **商品元信息每次请求查一次 MySQL**（主键查询），库存扣减才走 Redis；如需进一步压榨 QPS，可把活动时间缓存到 Redis；
- **启动会覆盖 Redis 库存**：`initStockToRedis()` 每次启动按数据库库存重置，重启前应确保队列已清空；
- **死信队列只做隔离**：`seckill.order.dlq` 堆积后需要人工排查/重放，暂未提供管理页面；重试间隔固定 5s，没有指数退避；
- **无支付/关单流程**，订单状态仅用于演示。

## 相关文档

- [压测方法与实测结果](benchmark/README.md)
- [架构说明](docs/architecture.md)
- [踩坑记录](docs/踩坑记录.md)
