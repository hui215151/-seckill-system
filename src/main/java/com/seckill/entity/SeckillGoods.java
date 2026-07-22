package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillGoods {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String goodsName;
    private String goodsImg;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    @Version
    private Integer version;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
