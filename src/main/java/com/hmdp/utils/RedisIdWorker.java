package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 一个自行实现的Redis分布式ID生成器
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-10 17:20
 */

@Component
public class RedisIdWorker {
    public static final long BEGIN_TIMESTAMP = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            .toEpochSecond(ZoneOffset.UTC);
    public static final int COUNT_BITS = 32; // 序列号占用的位数

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取一个 id。
     * <p>在 redis 中会以 key 和 日期划分计时器</p>
     * @param key 通常会以业务划分 id
     * @return
     */
    public long nextId(String key) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = epochSecond - BEGIN_TIMESTAMP;

        // 通过 redis 生成序号
        String date = now.format(DateTimeFormatter.ofPattern("yyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("counter:" + key + ":" + date);

        return timeStamp << COUNT_BITS | count;
    }
}
