package com.chaosblade.svc.taskexecutor.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简易 Long ID 生成器（基于时间戳 + 自增序列），用于非自增主键表。
 * 非分布式强一致雪花实现，但在单实例内具备足够的唯一性。
 */
public final class Ids {
    private static final AtomicLong SEQ = new AtomicLong(ThreadLocalRandom.current().nextInt(1 << 10));
    private Ids() {}

    public static long newId() {
        long ts = System.currentTimeMillis(); // 毫秒
        long seq = SEQ.incrementAndGet() & ((1L << 20) - 1); // 20bit 序列
        long id = (ts << 20) | seq;
        return id & Long.MAX_VALUE; // 保证正数
    }
}

