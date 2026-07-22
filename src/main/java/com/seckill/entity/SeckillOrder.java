package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    private Long goodsId;
    private String goodsName;
    private BigDecimal seckillPrice;
    private Integer status; // 0-创建中 1-已支付 2-已取消
    private LocalDateTime createTime;
    private LocalDateTime payTime;
}
