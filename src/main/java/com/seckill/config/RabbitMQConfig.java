package com.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
    
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).build();
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
}
