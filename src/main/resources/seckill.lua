--参数列表:这部分定义了脚本接收的动态参数，会在执行时从外部传入：
local voucherId=ARGV[1]
local userId=ARGV[2]
--这部分定义了脚本中要操作的Redis键，采用字符串拼接方式生成：
local stockKey ='seckill:stock:' .. voucherId
local orderKey ='seckill:order:' .. voucherId

--if (tonumber(redis.call('get',stockKey))<=0) then
--    return 1
--end

-- 安全获取库存值
local stock = redis.call('get', stockKey)
if stock == false or tonumber(stock) <= 0 then
    return 1 -- 库存不存在或不足
end

--判断用户是否重复下单
if (redis.call('sismember',orderKey,userId)==1) then
    return 2
end

redis.call('incrby',stockKey,-1)

redis.call('sadd',orderKey,userId)

return 0

