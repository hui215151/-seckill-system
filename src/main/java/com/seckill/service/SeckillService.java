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
}
