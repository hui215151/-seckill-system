package com.seckill.support;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 秒杀活动时间窗口判断，独立成无状态工具类，方便单元测试。
 *
 * 约定：
 * - 开始时间包含（now >= start 即视为开始）
 * - 结束时间包含（now <= end 仍可下单）
 * - 时间字段为 null 时视为「不限制」
 */
public final class SeckillWindow {

    /** 防重 key 至少保留 60 秒，避免活动结束瞬间 TTL 为 0 导致 key 立即失效 */
    private static final long MIN_TTL_SECONDS = 60L;

    private SeckillWindow() {
    }

    public static boolean isStarted(LocalDateTime start, LocalDateTime now) {
        return start == null || !now.isBefore(start);
    }

    public static boolean isEnded(LocalDateTime end, LocalDateTime now) {
        return end != null && now.isAfter(end);
    }

    public static boolean isActive(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        return isStarted(start, now) && !isEnded(end, now);
    }

    /**
     * 防重 key 的存活时间：到活动结束再多留 60 秒，最少 60 秒。
     */
    public static long ttlSeconds(LocalDateTime end, LocalDateTime now) {
        if (end == null) {
            return MIN_TTL_SECONDS;
        }
        long seconds = Duration.between(now, end).getSeconds() + MIN_TTL_SECONDS;
        return Math.max(MIN_TTL_SECONDS, seconds);
    }
}
