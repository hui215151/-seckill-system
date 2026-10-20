package com.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    /**
     * 默认 SimpleMessageConverter 只支持 String/byte[]/Serializable，
     * 会把 SeckillMessage 拒掉；改用 JSON 序列化。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    // 秒杀订单队列
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";
    
    // 死信 / 重试拓扑
    public static final String DLX = "seckill.dlx";
    public static final String RETRY_QUEUE = "seckill.order.retry";
    public static final String RETRY_ROUTING_KEY = "seckill.order.retry";
    public static final String DLQ = "seckill.order.dlq";
    public static final String DLQ_ROUTING_KEY = "seckill.order.dlq";
    
    @Value("${seckill.mq.retry-ttl-ms:5000}")
    private long retryTtlMs;
    
    /**
     * 主队列：消费失败（nack 不重回队列）时进入死信交换机，
     * 由死信交换机路由到重试队列，而不是立刻丢弃。
     */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RETRY_ROUTING_KEY)
                .build();
    }
    
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }
    
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }
    
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }
    
    /**
     * 重试队列：消息在此停留 retryTtlMs，到期后重新投递回主交换机。
     */
    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl((int) retryTtlMs)
                .deadLetterExchange(SECKILL_EXCHANGE)
                .deadLetterRoutingKey(SECKILL_ROUTING_KEY)
                .build();
    }
    
    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue())
                .to(deadLetterExchange())
                .with(RETRY_ROUTING_KEY);
    }
    
    /**
     * 死信队列：重试次数用尽后在此隔离，人工排查或补偿。
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }
    
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }
}
