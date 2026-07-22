package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.TransactionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionLogMapper extends BaseMapper<TransactionLog> {
}
