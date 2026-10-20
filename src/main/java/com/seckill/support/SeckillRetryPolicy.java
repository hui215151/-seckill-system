package com.seckill.support;

import java.util.List;
import java.util.Map;

/**
 * 消费重试策略：从 RabbitMQ 的 x-death 头里统计「在重试队列中死过几次」。
 *
 * x-death 是 List<Map>，每个元素形如：
 * {queue -> seckill.order.retry, reason -> expired, count -> 2, ...}
 */
public final class SeckillRetryPolicy {

    private SeckillRetryPolicy() {
    }

    public static int retryCount(List<?> xDeath, String retryQueue) {
        if (xDeath == null || retryQueue == null) {
            return 0;
        }
        int total = 0;
        for (Object entry : xDeath) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) entry;
            if (!retryQueue.equals(String.valueOf(map.get("queue")))) {
                continue;
            }
            Object count = map.get("count");
            if (count instanceof Number) {
                total += ((Number) count).intValue();
            }
        }
        return total;
    }

    public static boolean shouldRetry(int attempts, int maxRetries) {
        return attempts < maxRetries;
    }
}
