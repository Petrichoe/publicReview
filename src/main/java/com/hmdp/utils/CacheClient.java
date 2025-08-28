package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 封装Redis工具类：缓存击穿，缓存穿透
 */
@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;



    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){  //返回类型不确定时用泛型
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if(StrUtil.isNotBlank(Json)){//blank表示就算为" "也不行只能什么都没有
            return JSONUtil.toBean(Json, type);
        }
        //判断命中的是否为空值->判断 "" 这个的，如果是null表示还没从数据库查询，如果不是null那只能是空值:""
        if (Json!=null){
            return null;
        }
        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);//有时候查shop有时查user所以不能固定
        //如果id查询的店铺不存在
        if(r==null){
            //将空值存入redis中，避免缓存穿透问题
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //店铺存在，就将其放入缓存中
        this.set(key, r, time, unit);
        return r;

    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID>R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if(StrUtil.isBlank(shopJson)){//blank表示就算为" "也不行只能什么都没有
            return null;
        }
        //命中，把json反序列化为对象:以便于在Java中进行判断
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return r;
        }
        //过期
        //获取锁
        String lockkey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean islock=tryLock(lockkey);
        if (islock){
            //如果获取到锁,开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    R r1= dbFallback.apply(id);//查询数据库
                    //写入Redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockkey);
                }
            });

        }
        return r;
    }



    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }



}
