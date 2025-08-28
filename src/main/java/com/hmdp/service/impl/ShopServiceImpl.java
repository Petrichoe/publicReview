package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop=queryWithLogicalExpire(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }

    /**
     * 避免缓存穿透代码
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if(StrUtil.isNotBlank(shopJson)){//blank表示就算为" "也不行只能什么都没有
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //判断命中的是否为空值->判断 "" 这个的，如果是null表示还没从数据库查询，如果不是null那只能是空值:""
        if (shopJson!=null){
            return null;
        }
        //不存在，根据id查询数据库
        Shop shop = getById(id);
        //如果id查询的店铺不存在
        if(shop==null){
            //将空值存入redis中，避免缓存穿透问题
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //店铺存在，就将其放入缓存中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//转为JSON字符串后存入

        return shop;

    }

    /**
     * 利用互斥锁解决缓存击穿代码
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if(StrUtil.isNotBlank(shopJson)){//blank表示就算为" "也不行只能什么都没有
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否为空值->判断 "" 这个的，如果是null表示还没从数据库查询，如果不是null那只能是空值:""
        if (shopJson!=null){
            return null;
        }

    //没命中:实现缓存重建
        //获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop = null;
        try {
            boolean islock=tryLock(lockKey);
            if (!islock){
                //休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //成功，根据id查询数据库
            shop = getById(id);
            //模拟数据库查询的时间
            Thread.sleep(200);
            //如果id查询的店铺不存在
            if(shop==null){
                //将空值存入redis中，避免缓存穿透问题
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //店铺存在，就将其放入缓存中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);//转为JSON字符串后存入
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放锁
            unlock(lockKey);
        }

        return shop;
    }


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 利用逻辑过期解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key=RedisConstants.CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存是否命中
        if(StrUtil.isBlank(shopJson)){//blank表示就算为" "也不行只能什么都没有
            return null;
        }
        //命中，把json反序列化为对象:以便于在Java中进行判断
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop1 = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return shop1;
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
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockkey);
                }
            });

        }
        return shop1;
    }



    /**
     * 自己封装逻辑过期时间
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));//时间可以我们自己定让外部的人传

        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if (id==null){
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
