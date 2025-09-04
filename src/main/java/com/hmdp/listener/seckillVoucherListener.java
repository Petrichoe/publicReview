package com.hmdp.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class seckillVoucherListener {

    // 注入实际处理订单业务的 Service
    private final IVoucherOrderService voucherOrderService;
    private final RedissonClient redissonClient; // 注入 RedissonClient

    // 注入 VoucherOrderMapper
    private final VoucherOrderMapper voucherOrderMapper;

    @RabbitListener(bindings =@QueueBinding(
            value = @Queue(name = "voucher.queue",durable = "true",
            // 关键配置：声明死信交换机和路由键
            arguments = {
                    @Argument(name = "x-dead-letter-exchange", value = "dlx.direct"),
                    @Argument(name = "x-dead-letter-routing-key", value = "dlk.voucher")
            }),
            exchange = @Exchange(name = "voucher.direct"),
            key = "red" // <--- 明确指定绑定键
    ))
    public void listenseckillvoucher(VoucherOrder voucherOrder, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (voucherOrder==null){
            log.error("从MQ收到空的秒杀凭证订单消息");
            return;
        }

        // 1. 前置幂等性检查
        Long count = voucherOrderMapper.selectCount(new QueryWrapper<VoucherOrder>().eq("id", voucherOrder.getId()));
        Long userId = voucherOrder.getUserId();
        if (count > 0) {
            log.warn("订单已存在，直接ACK，忽略重复消息: {}", userId);
            try {
                channel.basicAck(deliveryTag, false);
            }catch (Exception e){
                log.error("尝试ACK重复消息时发生错误，可能已被确认。Error: {}", e.getMessage());
            }


            return;
        }

        // 2. 如果不存在，才开始获取锁、执行业务
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());

        boolean isLockAcquired = false;
        try {
            // 尝试获取锁，等待1秒，锁自动释放时间10秒 (根据业务调整)
            isLockAcquired = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!isLockAcquired) {
                log.error("获取订单锁失败 (userId: {}): {}", userId, voucherOrder);
                // 【手动拒绝位置①】: 获取锁失败，拒绝消息，让它进入死信队列
                channel.basicNack(deliveryTag, false, false);
                return;
            }
            voucherOrderService.createVoucherOrder(voucherOrder);

            // 核心：所有操作成功后，手动确认消息
            channel.basicAck(deliveryTag, false); // false表示只确认当前这一条
            log.info("MQ消费者处理订单成功，手动ACK: {}", voucherOrder);


        }catch (InterruptedException e) {
            // 单独捕获 InterruptedException 如网络抖动
            log.error("处理订单时线程被中断 (userId: {}): {}", userId, voucherOrder, e);
            // 拒绝消息，并让它【重新入队】，以便应用重启后或其他消费者能再次尝试
            channel.basicNack(deliveryTag, false, true);
            // 恢复线程的中断状态，这是处理该异常的最佳实践
            Thread.currentThread().interrupt();

        } catch (Exception e) {
            log.error("处理秒杀凭证订单时发生异常 (userId: {}): {}", userId, voucherOrder, e);
            //  发生其他任何业务异常，拒绝消息，让它进入死信队列，省得多次重试
            channel.basicNack(deliveryTag, false, false);
        } finally {
            if (isLockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

}
