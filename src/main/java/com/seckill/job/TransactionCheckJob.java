package com.seckill.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seckill.entity.TransactionLog;
import com.seckill.mapper.TransactionLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class TransactionCheckJob {
    
    @Autowired
    private TransactionLogMapper transactionLogMapper;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    
    /**
     * 每 5 分钟扫描一次失败的事务，回补 Redis 库存
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void checkFailedTransaction() {
        QueryWrapper<TransactionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 2)  // 失败状态
               .lt("create_time", LocalDateTime.now().minusMinutes(1)); // 1分钟前的
        
        List<TransactionLog> failedList = transactionLogMapper.selectList(wrapper);
        
        for (TransactionLog log : failedList) {
            try {
                // 解析 businessId 获取 goodsId
                String[] parts = log.getBusinessId().split(":");
                Long goodsId = Long.valueOf(parts[1]);
                
                // 回补 Redis 库存
                String stockKey = STOCK_KEY_PREFIX + goodsId;
                redisTemplate.opsForValue().increment(stockKey);
                
                // 更新事务表为已处理
                log.setStatus(3); // 3-已回补
                transactionLogMapper.updateById(log);
                
                log.info("库存回补成功：transactionId={}, goodsId={}", 
                        log.getTransactionId(), goodsId);
                
            } catch (Exception e) {
                log.error("库存回补失败：{}", e.getMessage());
            }
        }
    }
}
