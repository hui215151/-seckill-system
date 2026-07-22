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
            log.info("收到秒杀消息：{}", message);
            seckillService.createOrder(message);
            // 手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消费失败：{}", e.getMessage());
            // 拒绝消息，重新入队（或根据重试次数决定是否丢弃）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
