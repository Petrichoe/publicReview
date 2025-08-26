package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class seckillVoucherListener {

    // 注入实际处理订单业务的 Service
    private final IVoucherOrderService voucherOrderService;
    private final RedissonClient redissonClient; // 注入 RedissonClient

    @RabbitListener(bindings =@QueueBinding(
            value = @Queue(name = "voucher.queue",durable = "true"),
            exchange = @Exchange(name = "voucher.direct"),
            key = "red" // <--- 明确指定绑定键
    ))
    public void listenseckillvoucher(VoucherOrder voucherOrder){
        if (voucherOrder==null){
            log.error("从MQ收到空的秒杀凭证订单消息");
            return;
        }
        // --- 以下是之前讨论的订单处理逻辑，现在由监听器直接调用 ---
        Long userId = voucherOrder.getUserId();
        // 对于 RedissonClient 的使用，确保其已正确配置并注入
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLockAcquired = false;
        try {
            // 尝试获取锁，等待1秒，锁自动释放时间10秒 (根据业务调整)
            isLockAcquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!isLockAcquired) {
                log.error("获取订单锁失败 (userId: {}): {}", userId, voucherOrder);
                // 根据业务需求，可以考虑将消息发送到死信队列或记录后丢弃
                // 例如: throw new AmqpRejectAndDontRequeueException("获取锁失败，订单处理终止");
                return;
            }

            // 通过代理对象调用事务方法
            // 注意：createVoucherOrder 方法现在直接在 IVoucherOrderService 接口及其实现中
            // 之前我们讨论的是 handleVoucherOrder 和 createVoucherOrder 两个方法
            // 这里简化为直接调用 IVoucherOrderService 中的方法
            // 假设 IVoucherOrderService 中有一个名为 processSeckillOrder 的方法包含完整的业务逻辑
            // proxy.createVoucherOrder(voucherOrder); // 这是之前讨论的方法
            // 如果IVoucherOrderService中直接有处理逻辑并标记了@Transactional的方法，可以直接调用
            // 例如，假设IVoucherOrderService有一个方法 processSeckillOrder(VoucherOrder order)
            // 并且这个方法本身是 @Transactional 的

            // 直接调用注入的 voucherOrderService 的方法
            // Spring 容器会自动处理其 @Transactional 注解
            voucherOrderService.createVoucherOrder(voucherOrder);
            log.info("MQ消费者处理订单成功: {}", voucherOrder); // 添加成功日志


        } catch (InterruptedException e) {
            log.error("获取分布式锁时被中断 (userId: {}): {}", userId, voucherOrder, e);
            Thread.currentThread().interrupt();
            // 考虑消息重试或死信
        } catch (Exception e) {
            log.error("处理秒杀凭证订单时发生异常 (userId: {}): {}", userId, voucherOrder, e);
            // 抛出异常，让RabbitMQ根据配置进行重试或发送到死信队列
            throw e;
        } finally {
            if (isLockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

}
