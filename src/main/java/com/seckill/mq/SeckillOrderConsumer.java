package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.service.impl.SeckillServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SeckillOrderConsumer {
    
    @Autowired
    private SeckillServiceImpl seckillService;
    
    @RabbitListener(queues = "seckill.order.queue")
    public void handleMessage(SeckillServiceImpl.SeckillMessage message, 
                              Message mqMessage, 
                              Channel channel) throws IOException {
        long deliveryTag = mqMessage.getMessageProperties().getDeliveryTag();
        
        try {
            // 幂等：事务已成功/已失败/不存在（如手工清库）时直接确认并跳过，
            // 避免重复建单和「孤儿消息」凭库存继续下单。
            if (!seckillService.isTransactionPending(message.getTransactionId())) {
                log.warn("跳过重复或已失效的消息：transactionId={}", message.getTransactionId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            
            log.info("收到秒杀消息：{}", message);
            seckillService.createOrder(message);
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("消费失败，丢弃该消息（事务表已记录失败，由对账任务回补库存）：{}", e.getMessage());
            // requeue=false：避免一条失败消息无限重投堵死队列。
            // 失败状态已由 createOrder 写入 transaction_log，定时任务会回补 Redis 库存。
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
