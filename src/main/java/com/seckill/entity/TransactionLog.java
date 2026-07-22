package com.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransactionLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String transactionId;
    private String businessType;
    private String businessId;
    private Integer status; // 0-处理中 1-成功 2-失败
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
