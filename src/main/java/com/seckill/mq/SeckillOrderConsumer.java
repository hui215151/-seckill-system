package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.config.RabbitMQConfig;
import com.seckill.service.impl.SeckillServiceImpl;
import com.seckill.support.SeckillRetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class SeckillOrderConsumer {
    
    @Autowired
    private SeckillServiceImpl seckillService;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Value("${seckill.mq.max-retries:3}")
    private int maxRetries;
    
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleMessage(SeckillServiceImpl.SeckillMessage message, 
                              Message mqMessage, 
                              Channel channel) throws IOException {
        long deliveryTag = mqMessage.getMessageProperties().getDeliveryTag();
        String transactionId = message.getTransactionId();
        
        try {
            // 幂等：事务已成功或记录不存在（如手工清库）时直接确认并跳过，
            // 避免重复建单和「孤儿消息」凭库存继续下单。
            if (!seckillService.isTransactionPending(transactionId)) {
                log.warn("跳过重复或已失效的消息：transactionId={}", transactionId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            
            log.info("收到秒杀消息：{}", message);
            seckillService.createOrder(message);
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            // 失败状态用独立事务落库，避免被 createOrder 的事务回滚
            try {
                seckillService.markTransactionFailed(transactionId);
            } catch (Exception markError) {
                log.error("记录失败事务异常：{}", markError.getMessage());
            }
            
            int attempts = SeckillRetryPolicy.retryCount(
                    asList(mqMessage.getMessageProperties().getHeader("x-death")),
                    RabbitMQConfig.RETRY_QUEUE);
            
            if (SeckillRetryPolicy.shouldRetry(attempts, maxRetries)) {
                log.warn("消费失败（已重试 {} 次），转入重试队列：{}", attempts, e.getMessage());
                // requeue=false：交由死信交换机路由到重试队列（TTL 到期后回主队列）
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.error("重试 {} 次仍失败，转入死信队列：{}", attempts, e.getMessage());
                rabbitTemplate.send(RabbitMQConfig.DLX, RabbitMQConfig.DLQ_ROUTING_KEY, mqMessage);
                channel.basicAck(deliveryTag, false);
            }
        }
    }
    
    private List<?> asList(Object header) {
        return header instanceof List ? (List<?>) header : null;
    }
}
