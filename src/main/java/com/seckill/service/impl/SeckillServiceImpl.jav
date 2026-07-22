package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seckill.config.RabbitMQConfig;
import com.seckill.config.RedisConfig;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.TransactionLog;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mapper.TransactionLogMapper;
import com.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private DefaultRedisScript<Long> stockDeductScript;
    
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    
    @Autowired
    private TransactionLogMapper transactionLogMapper;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SECKILL_KEY_PREFIX = "seckill:user:";
    private static final String ORDER_STATUS_KEY_PREFIX = "seckill:order:status:";
    
    /**
     * 系统启动时，将库存预热到 Redis
     */
    @PostConstruct
    public void initStockToRedis() {
        seckillGoodsMapper.selectList(null).forEach(goods -> {
            String key = STOCK_KEY_PREFIX + goods.getId();
            redisTemplate.opsForValue().set(key, goods.getStockCount());
            log.info("库存预热：商品{}，库存{}", goods.getGoodsName(), goods.getStockCount());
        });
    }
    
    @Override
    public SeckillOrder doSeckill(Long userId, Long goodsId) {
        // 1. 防重：用户同一商品只能秒杀一次
        String userKey = USER_SECKILL_KEY_PREFIX + userId + ":" + goodsId;
        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(userKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isFirst)) {
            throw new RuntimeException("请勿重复秒杀");
        }
        
        // 2. Redis 预扣库存（Lua 脚本保证原子性）
        String stockKey = STOCK_KEY_PREFIX + goodsId;
        Long remainStock = redisTemplate.execute(
                stockDeductScript,
                Collections.singletonList(stockKey),
                "1"
        );
        
        if (remainStock == null || remainStock < 0) {
            // 库存不足，删除防重标记
            redisTemplate.delete(userKey);
            throw new RuntimeException("商品已售罄");
        }
        
        // 3. 生成事务ID，发送 MQ 异步创建订单
        String transactionId = UUID.randomUUID().toString().replace("-", "");
        
        // 写入本地事务表
        TransactionLog logEntry = new TransactionLog();
        logEntry.setTransactionId(transactionId);
        logEntry.setBusinessType("SECKILL_ORDER");
        logEntry.setBusinessId(userId + ":" + goodsId);
        logEntry.setStatus(0);
        transactionLogMapper.insert(logEntry);
        
        // 发送 MQ
        SeckillMessage message = new SeckillMessage();
        message.setTransactionId(transactionId);
        message.setUserId(userId);
        message.setGoodsId(goodsId);
        
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                message
        );
        
        // 4. 写入订单状态到 Redis（用户轮询查询）
        String statusKey = ORDER_STATUS_KEY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(statusKey, "PROCESSING", 10, TimeUnit.MINUTES);
        
        // 返回一个临时订单对象（状态为处理中）
        SeckillOrder tempOrder = new SeckillOrder();
        tempOrder.setUserId(userId);
        tempOrder.setGoodsId(goodsId);
        tempOrder.setStatus(0);
        
        return tempOrder;
    }
    
    @Override
    public SeckillOrder getSeckillResult(Long userId, Long goodsId) {
        // 查询最新订单
        QueryWrapper<SeckillOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("goods_id", goodsId)
               .orderByDesc("create_time").last("limit 1");
        return seckillOrderMapper.selectOne(wrapper);
    }
    
    /**
     * MQ 消费者调用：创建订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(SeckillMessage message) {
        try {
            Long goodsId = message.getGoodsId();
            Long userId = message.getUserId();
            String transactionId = message.getTransactionId();
            
            // 查询商品信息
            SeckillGoods goods = seckillGoodsMapper.selectById(goodsId);
            if (goods == null || goods.getStockCount() <= 0) {
                throw new RuntimeException("商品库存不足");
            }
            
            // 乐观锁扣减数据库库存
            int affected = seckillGoodsMapper.deductStock(goodsId, goods.getVersion());
            if (affected <= 0) {
                throw new RuntimeException("库存扣减失败，可能已被其他用户抢购");
            }
            
            // 创建订单
            SeckillOrder order = new SeckillOrder();
            order.setUserId(userId);
            order.setGoodsId(goodsId);
            order.setGoodsName(goods.getGoodsName());
            order.setSeckillPrice(goods.getSeckillPrice());
            order.setStatus(0);
            seckillOrderMapper.insert(order);
            
            // 更新事务表为成功
            TransactionLog logEntry = new TransactionLog();
            logEntry.setTransactionId(transactionId);
            logEntry.setStatus(1);
            transactionLogMapper.updateById(logEntry);
            
            // 更新 Redis 订单状态
            String statusKey = ORDER_STATUS_KEY_PREFIX + transactionId;
            redisTemplate.opsForValue().set(statusKey, "SUCCESS", 10, TimeUnit.MINUTES);
            
            log.info("订单创建成功：userId={}, goodsId={}", userId, goodsId);
            
        } catch (Exception e) {
            log.error("订单创建失败：{}", e.getMessage());
            // 更新事务表为失败（定时任务会扫描回补 Redis 库存）
            TransactionLog logEntry = new TransactionLog();
            logEntry.setTransactionId(message.getTransactionId());
            logEntry.setStatus(2);
            transactionLogMapper.updateById(logEntry);
            throw e;
        }
    }
    
    /**
     * MQ 消息对象
     */
    @lombok.Data
    public static class SeckillMessage {
        private String transactionId;
        private Long userId;
        private Long goodsId;
    }
}
