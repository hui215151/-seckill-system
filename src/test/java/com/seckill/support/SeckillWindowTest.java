package com.seckill.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeckillWindowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0, 0);

    @Test
    void notStartedBeforeStartTime() {
        LocalDateTime start = NOW.plusMinutes(1);
        LocalDateTime end = NOW.plusHours(1);

        assertFalse(SeckillWindow.isStarted(start, NOW));
        assertFalse(SeckillWindow.isActive(start, end, NOW));
    }

    @Test
    void startedAtExactStartTime() {
        assertTrue(SeckillWindow.isStarted(NOW, NOW));
    }

    @Test
    void activeInsideWindow() {
        assertTrue(SeckillWindow.isActive(NOW.minusHours(1), NOW.plusHours(1), NOW));
    }

    @Test
    void activeAtExactEndTime() {
        // 结束时间按包含处理，最后一秒仍可下单
        assertTrue(SeckillWindow.isActive(NOW.minusHours(1), NOW, NOW));
        assertFalse(SeckillWindow.isEnded(NOW, NOW));
    }

    @Test
    void endedAfterEndTime() {
        LocalDateTime end = NOW.minusSeconds(1);

        assertTrue(SeckillWindow.isEnded(end, NOW));
        assertFalse(SeckillWindow.isActive(NOW.minusHours(1), end, NOW));
    }

    @Test
    void nullWindowMeansUnlimited() {
        assertTrue(SeckillWindow.isStarted(null, NOW));
        assertFalse(SeckillWindow.isEnded(null, NOW));
        assertTrue(SeckillWindow.isActive(null, null, NOW));
    }

    @Test
    void ttlCoversUntilEndPlusGrace() {
        assertEquals(160L, SeckillWindow.ttlSeconds(NOW.plusSeconds(100), NOW));
    }

    @Test
    void ttlNeverBelowMinimum() {
        assertEquals(60L, SeckillWindow.ttlSeconds(NOW.minusSeconds(10), NOW));
        assertEquals(60L, SeckillWindow.ttlSeconds(null, NOW));
    }
}
