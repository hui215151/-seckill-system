package com.seckill.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeckillRetryPolicyTest {

    private static final String RETRY_QUEUE = "seckill.order.retry";

    @Test
    void noDeathHeaderMeansZeroAttempts() {
        assertEquals(0, SeckillRetryPolicy.retryCount(null, RETRY_QUEUE));
    }

    @Test
    void countsOnlyMatchingQueue() {
        List<Map<String, Object>> xDeath = new ArrayList<>();
        xDeath.add(entry(RETRY_QUEUE, 2));
        xDeath.add(entry("other.queue", 5));

        assertEquals(2, SeckillRetryPolicy.retryCount(xDeath, RETRY_QUEUE));
    }

    @Test
    void sumsMultipleEntriesForSameQueue() {
        List<Map<String, Object>> xDeath = new ArrayList<>();
        xDeath.add(entry(RETRY_QUEUE, 1));
        xDeath.add(entry(RETRY_QUEUE, 2));

        assertEquals(3, SeckillRetryPolicy.retryCount(xDeath, RETRY_QUEUE));
    }

    @Test
    void ignoresMalformedEntries() {
        List<Object> xDeath = new ArrayList<>();
        xDeath.add("not-a-map");
        xDeath.add(entry(RETRY_QUEUE, 1));

        assertEquals(1, SeckillRetryPolicy.retryCount(xDeath, RETRY_QUEUE));
    }

    @Test
    void retriesUntilMaxAttempts() {
        assertTrue(SeckillRetryPolicy.shouldRetry(0, 3));
        assertTrue(SeckillRetryPolicy.shouldRetry(2, 3));
        assertFalse(SeckillRetryPolicy.shouldRetry(3, 3));
        assertFalse(SeckillRetryPolicy.shouldRetry(4, 3));
    }

    private Map<String, Object> entry(String queue, int count) {
        Map<String, Object> map = new HashMap<>();
        map.put("queue", queue);
        map.put("count", (long) count);
        return map;
    }
}
