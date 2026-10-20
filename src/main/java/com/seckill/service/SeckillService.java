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
     * 事务是否仍可继续消费。
     * 0-处理中、2-曾失败待重试 返回 true；1-成功或记录不存在返回 false。
     */
    boolean isTransactionPending(String transactionId);
    
    /**
     * 记录消费失败（独立事务提交，避免被消费事务回滚）。
     */
    void markTransactionFailed(String transactionId);
}
