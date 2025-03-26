package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查寻优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime localDateTime=LocalDateTime.now();

        if (voucher.getBeginTime().isAfter(localDateTime)){//开始时间比当前时间晚
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否已结束
        if(voucher.getEndTime().isBefore(localDateTime)){//结束时间比当前时间早
              if (voucher.getStock()<1){
                  return Result.fail("库存不足");
              }
        }

        Long userid = UserHolder.getUser().getId();
        ///问题：为什么锁要放这里：等事务提交后再去释放锁
        synchronized (userid.toString().intern()) {//用intern()保证只要userid一样就是同一把锁.确保不同线程使用相同的锁对象，保证 synchronized 锁住的是同一个用户的请求
            //返回订单id
            //问题：Service类中在A方法中调用声明式事务B时，B的事务不会生效
            //Spring AOP 事务管理的原理是 代理对象 来管理事务：因此需要获取当前类的 Spring AOP 代理对象（如果用this直接调用就不行），否则调用方法的事务就不能生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
//Spring AOP 事务的本质是：代理对象拦截方法调用，并在方法执行前后管理事务。

    @Transactional
    public  Result createVoucherOrder(Long voucherId){
        //一人一票，判断是否重复购买
        Long userid = UserHolder.getUser().getId();

            Integer count = query()
                    .eq("user_id", userid)
                    .eq("voucher_id", voucherId)
                    .count();

            if (count > 0) {
                return Result.fail("已购买过，不能再买");
            }

            //扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("库存不足");
            }

            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userid);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            return Result.ok(orderId);

    }
}
