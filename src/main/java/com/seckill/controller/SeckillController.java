package com.seckill.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {
    
    // 令牌桶限流：每秒放行 100 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(100);
    
    @Autowired
    private SeckillService seckillService;
    
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    
    /**
     * 获取秒杀商品列表
     */
    @GetMapping("/goods")
    public List<SeckillGoods> listGoods() {
        return seckillGoodsMapper.selectList(null);
    }
    
    /**
     * 执行秒杀
     */
    @PostMapping("/do")
    public SeckillOrder doSeckill(@RequestParam Long userId, 
                                   @RequestParam Long goodsId) {
        // 1. 限流
        if (!rateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new RuntimeException("活动太火爆，请稍后重试");
        }
        
        // 2. 执行秒杀
        return seckillService.doSeckill(userId, goodsId);
    }
    
    /**
     * 查询秒杀结果
     */
    @GetMapping("/result")
    public SeckillOrder getResult(@RequestParam Long userId, 
                                   @RequestParam Long goodsId) {
        return seckillService.getSeckillResult(userId, goodsId);
    }
}
