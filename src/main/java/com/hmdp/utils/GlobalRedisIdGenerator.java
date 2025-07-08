package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-05-28 19:52
 */

@Component
public class GlobalRedisIdGenerator {
    private static final String ID_COUNTER_PREFIX = "counter:id:";
    Long startTime = 1735689600L;       // 一个以 second 计数的起始时间戳

    @Resource
    private RedisUtil redisUtil;

    /**
     * 生成 id
     * @param prefix 区分表，避免 id 冲突
     * @return
     */
    public Long nextId(String prefix){
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - startTime;
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 利用  redis 的自增功能的原子性 来生成自增 id。
        // 同时每天  自增 id 的起始值从 0 开始。
        long sequence = redisUtil.increment(ID_COUNTER_PREFIX + prefix + ":" + today, 1);
        return time << 32 | sequence;
    }

    public static void main(String[] args) {
        System.out.println(LocalDateTime.of(2025,1,1,0,0).toEpochSecond(ZoneOffset.UTC));
    }
}
