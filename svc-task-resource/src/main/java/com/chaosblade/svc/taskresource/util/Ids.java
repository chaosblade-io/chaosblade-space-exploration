/*
 * Copyright 2025 The ChaosBlade Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chaosblade.svc.taskresource.util;

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

