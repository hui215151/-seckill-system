package com.seckill.service;

import com.seckill.entity.SeckillOrder;

public interface SeckillService {
    
    /**
     * 执行秒杀
     */
    SeckillOrder doSeckill(Long userId, Long goodsId);
    
    /**
     * 查询秒杀结果
     */
    SeckillOrder getSeckillResult(Long userId, Long goodsId);
    
    /**
     * 事务是否仍处于「处理中」状态。
     * 用于 MQ 消费幂等：重复投递或已被清理的消息直接跳过。
     */
    boolean isTransactionPending(String transactionId);
}
