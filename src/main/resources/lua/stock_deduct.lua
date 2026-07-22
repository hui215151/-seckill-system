-- 秒杀库存扣减 Lua 脚本
-- KEYS[1]: 库存key  seckill:stock:goodsId
-- ARGV[1]: 扣减数量，默认1

local stockKey = KEYS[1]
local deductCount = tonumber(ARGV[1] or 1)

-- 获取当前库存
local stock = tonumber(redis.call('get', stockKey) or 0)

-- 库存不足
if stock <= 0 then
    return -1  -- 库存不足
end

-- 库存足够，扣减
local remain = redis.call('decrby', stockKey, deductCount)

if remain < 0 then
    -- 扣减后变负数，回滚
    redis.call('incrby', stockKey, deductCount)
    return -1
end

return remain  -- 返回剩余库存
