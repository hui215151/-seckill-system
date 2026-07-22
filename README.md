# 高并发秒杀系统

模拟电商大促秒杀场景，核心解决高并发下库存扣减一致性。

## 技术栈
- SpringBoot 2.7.x
- Redis 6.x
- RabbitMQ 3.x
- MySQL 8.x
- MyBatis-Plus
- JWT

## 核心亮点
| 问题 | 方案 |
|------|------|
| 超卖 | Redis Lua原子预扣 + MySQL乐观锁 + 本地事务表对账 |
| 高并发压垮数据库 | RabbitMQ异步削峰 |
| 重复点击/脚本刷单 | Guava RateLimiter令牌桶限流 + Redis SETNX防重 |

## 压测结果
- 1000并发，直接查库：TPS 50，超卖，响应时间 2s
- 1000并发，优化后：TPS 800，零超卖，响应时间 200ms

## 快速启动
1. 克隆项目：`git clone https://github.com/hui215151/-seckill-system.git`
2. 创建数据库，执行 `sql/seckill.sql`
3. 修改 `application.yml` 中的 Redis、MySQL、RabbitMQ 配置
4. 启动项目

## 接口文档
| 接口 | 说明 |
|------|------|
| POST /api/seckill/goods | 获取秒杀商品列表 |
| POST /api/seckill/do | 执行秒杀 |
| GET /api/seckill/result | 查询秒杀结果 |
