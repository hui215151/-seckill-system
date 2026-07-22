package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {
    
    // 乐观锁扣减库存
    @Update("UPDATE seckill_goods SET stock_count = stock_count - 1, version = version + 1 " +
            "WHERE id = #{goodsId} AND stock_count > 0 AND version = #{version}")
    int deductStock(@Param("goodsId") Long goodsId, @Param("version") Integer version);
}
