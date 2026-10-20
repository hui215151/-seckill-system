package com.seckill.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.security.JwtAuthInterceptor;
import com.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {
    
    /**
     * 令牌桶限流，QPS 可通过 seckill.rate-limit.qps 配置（默认 1000）。
     * 压测时可用 -Dseckill.rate-limit.qps=100000 临时放开。
     */
    @Value("${seckill.rate-limit.qps:1000}")
    private double rateLimitQps;
    
    private RateLimiter rateLimiter;
    
    @PostConstruct
    public void initRateLimiter() {
        this.rateLimiter = RateLimiter.create(rateLimitQps);
        log.info("秒杀限流已启用：{} QPS", rateLimitQps);
    }
    
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
    public SeckillOrder doSeckill(
            @RequestAttribute(JwtAuthInterceptor.ATTR_USER_ID) Long userId,
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
    public SeckillOrder getResult(
            @RequestAttribute(JwtAuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam Long goodsId) {
        return seckillService.getSeckillResult(userId, goodsId);
    }
}
