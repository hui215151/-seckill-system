# 架构说明

## 1. 组件

| 组件 | 职责 |
|---|---|
| Spring Boot | HTTP 接口、参数校验、限流、JWT 校验 |
| MySQL | 商品、订单、本地事务表、用户 |
| Redis | 库存预扣、防重 key、订单状态缓存 |
| RabbitMQ | 秒杀请求异步削峰，重试/死信兜底 |

## 2. 下单时序

```
Client ──login──▶ AuthController ──▶ MySQL(sys_user, BCrypt) ──▶ JWT
Client ──POST /do (Bearer)──▶ JwtAuthInterceptor
                                   │ 校验签名/过期，注入 userId
                                   ▼
                            SeckillController
                                   │ 1. RateLimiter 限流
                                   ▼
                            SeckillServiceImpl
                                   │ 2. 商品存在 + 活动时间（MySQL 主键查询）
                                   │ 3. SETNX 防重（TTL 到活动结束）
                                   │ 4. Lua 原子预扣库存（Redis）
                                   │ 5. INSERT transaction_log(status=0)
                                   │ 6. 发送 MQ
                                   ▼
                            RabbitMQ 主队列
                                   ▼
                            SeckillOrderConsumer
                                   │ 7. 幂等校验（事务状态不为 1 才处理）
                                   │ 8. createOrder（@Transactional）
                                   │    ├─ 查商品、乐观锁扣 DB 库存
                                   │    ├─ INSERT seckill_order
                                   │    └─ UPDATE transaction_log status=1
                                   ▼
                            ACK
```

## 3. 一致性设计

- **Redis 预扣**：Lua 脚本原子执行「查库存 → 扣减 → 不足回滚」，保证同一商品不超卖；
- **MySQL 兜底**：`deductStock(id, version)` 乐观锁，Redis 与 DB 不一致时 DB 仍不会扣成负数；
- **本地事务表**：下单请求先写 `transaction_log(status=0)`，消费成功改 1、失败改 2，用于对账；
- **对账任务**：`TransactionCheckJob` 每 5 分钟扫 `status=2` 且超过 1 分钟的记录，回补 Redis 库存；
- **幂等**：消费前检查事务状态；订单表 `uk_user_goods(user_id, goods_id)` 唯一索引兜底重复建单；
- **防重**：Redis SETNX，key 为 `seckill:user:{userId}:{goodsId}`，TTL 覆盖活动结束时间。

## 4. 失败处理

| 场景 | 行为 |
|---|---|
| Redis 预扣失败（售罄） | 删除防重 key，返回「商品已售罄」 |
| MQ 发送失败 | 事务表停留在 0，需人工/定时补偿（当前未实现发送重试） |
| 消费失败 | 写 `status=2` → 进入重试队列（TTL 5s），最多 3 次 |
| 重试仍失败 | 消息进 `seckill.order.dlq`，事务表为 2，对账任务回补 Redis 库存 |
| 重复消息 / 孤儿消息 | 事务不存在或已成功 → 直接 ACK 跳过 |
| token 缺失/过期/篡改 | 401，拦截器直接返回，不进入业务 |

## 5. 容量与瓶颈（本机实测）

- 200 并发、库存充足：约 340–360 TPS，P95 ≈ 700ms；
- 同步链路每请求包含：JWT 校验 + 商品主键查询 + 事务表写入 + MQ 发布；
- 主要瓶颈在 MySQL 写入与连接池争用，不在 Redis 扣减；
- 生产化方向：活动信息缓存、事务表批量写、MQ 发送确认与本地消息表、分库分表、多实例与分布式限流。

## 6. 已知边界

- 单机单实例，无分布式锁/分库分表；
- 启动时按 DB 库存覆盖 Redis，重启前需清空队列；
- 无支付/关单流程，订单状态仅演示；
- JWT 无刷新/注销，密钥单机配置。
