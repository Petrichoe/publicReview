package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    //创建一个固定大小为 500 的线程池。
    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        //创建一个 CountDownLatch，用于等待所有任务完成。
        CountDownLatch latch=new CountDownLatch(300);

        //定义一个任务，每个任务会调用 redisIdWorker.nextId("order") 100 次。
        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                //System.out.println("id="+id);

            }
        };
        //提交 300 个任务到线程池中执行。
        long begin=System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();//CountDownLatch latch=new CountDownLatch(300); 直到这里面的300变0
        long end=System.currentTimeMillis();
        System.out.println("end="+end);
    }



    @Test
    void testSaveShop() throws InterruptedException {
        //用于先存储热点key
        Shop shop=shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);


    }


}
