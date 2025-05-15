package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    //初始化seckill.lua
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT =new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//指定文件位置
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列
    //private BlockingQueue<VoucherOrder> orderTasks =new ArrayBlockingQueue<>(1024*1024);

    /**
     * 1.入口
     * @param voucherId
     * @return
     */
    //private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本---> 原本加锁为了原子性--->用了lua脚本自带了原子性
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断有无购买资格
        int r=result.intValue();
        if (r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        //有购买资格,把下单信息保存到阻塞队列当中
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

//        //放入阻塞队列
//        orderTasks.add(voucherOrder);//生产者
//        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //发送订单消息到RabbitMQ中
        String exchangeName="voucher.direct";
        String routingKey = "red";
        rabbitTemplate.convertAndSend(exchangeName,routingKey,voucherOrder);
        log.info("订单信息已发送到MQ: exchange='{}', routingKey='{}', order={}", exchangeName, routingKey, voucherOrder);

        //返回订单id
        return  Result.ok(orderId);
    }

    //创建现称池
   // private static final ExecutorService SECKILL_ORDER_EXECTUOR = Executors.newSingleThreadExecutor();
//    @PostConstruct
//    private void init(){
//        //SECKILL_ORDER_EXECTUOR.submit(new VoucherOrderHandler());
//        proxy = (IVoucherOrderService) AopContext.currentProxy();// 只初始化代理，不启动线程池
//    }
    //这个意思是不是启动了一个单线程的线程池，然后提交常驻任务（VoucherOrderHandler），
    // 然后这个任务中的  VoucherOrder voucherOrder = orderTasks.take();取到提交的任务时就开始一个一个处理


    /**
     * 这部分通过RabbitMQ来调用了
     */
//    /**
//     * 2.实现异步下单
//     */
//    private class VoucherOrderHandler implements Runnable{//消费者
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    //获取队列中的用户信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }

//    /**
//     * 3.加锁保护
//     * @param voucherOrder
//     */
//    private void handleVoucherOrder(VoucherOrder voucherOrder){
//        Long userId = voucherOrder.getUserId();
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userid);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //以防万一，所以加锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            log.error("不允许重复下单");
//            return ;
//        }
//        //返回订单id
//        try {
//            //问题：Service类中在A方法中调用声明式事务B时，B的事务不会生效
//            //Spring AOP 事务管理的原理是 代理对象 来管理事务：因此需要获取当前类的 Spring AOP 代理对象（如果用this直接调用就不行），否则调用方法的事务就不能生效
//
//            proxy.createVoucherOrder(voucherOrder);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//
//    }

    /**
     * 4.实现数据库的那部分操作，//加入阻塞队列中异步操作
     *这个方法是实际创建订单并操作数据库的地方，由MQ消费者调用（通过代理）
     * @param voucherOrder
     * @return
     */
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder){
        //一人一票，判断是否重复购买
        Long userid = voucherOrder.getUserId();

        Integer count = query()
                .eq("user_id", userid)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();

        if (count > 0) {
            log.error("用户已经购买过一次");
            return null;
        }

        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)//乐观锁 防止库存超卖（gt("stock", 0)）
                .update();
        if (!success) {
            log.error("库存不足！");
            return null;
        }

        save(voucherOrder);
        return Result.ok();
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查寻优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        LocalDateTime localDateTime=LocalDateTime.now();
//
//        if (voucher.getBeginTime().isAfter(localDateTime)){//开始时间比当前时间晚
//            return Result.fail("秒杀尚未开始");
//        }
//        //判断秒杀是否已结束
//        if(voucher.getEndTime().isBefore(localDateTime)){//结束时间比当前时间早
//              if (voucher.getStock()<1){
//                  return Result.fail("库存不足");
//              }
//        }
//
//        Long userid = UserHolder.getUser().getId();
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userid);
//        RLock lock = redissonClient.getLock("lock:order:" + userid);
//
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//            //返回订单id
//        try {
//            //问题：Service类中在A方法中调用声明式事务B时，B的事务不会生效
//            //Spring AOP 事务管理的原理是 代理对象 来管理事务：因此需要获取当前类的 Spring AOP 代理对象（如果用this直接调用就不行），否则调用方法的事务就不能生效
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        }finally {
//            lock.unlock();
//        }
//    }
//Spring AOP 事务的本质是：代理对象拦截方法调用，并在方法执行前后管理事务。

//    @Transactional
//    public  Result createVoucherOrder(Long voucherId){
//        //一人一票，判断是否重复购买
//        Long userid = UserHolder.getUser().getId();
//
//            Integer count = query()
//                    .eq("user_id", userid)
//                    .eq("voucher_id", voucherId)
//                    .count();
//
//            if (count > 0) {
//                return Result.fail("已购买过，不能再买");
//            }
//
//            //扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock=stock-1")
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0)
//                    .update();
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//            //创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userid);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//
//            return Result.ok(orderId);
//
//    }


}
