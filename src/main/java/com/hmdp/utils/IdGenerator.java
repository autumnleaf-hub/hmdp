package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson的唯一ID生成器
 * 可以生成按日期前缀的递增ID
 */
@Slf4j
@Component
public class IdGenerator {
    private final RedissonClient redissonClient;

    public IdGenerator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 生成指定前缀的唯一ID
     * @param keyPrefix ID前缀
     * @return 生成的唯一ID
     */
    public long nextId(String keyPrefix) {
        // 1.构建key
        LocalDate now = LocalDate.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "id:" + keyPrefix + ":" + date;

        // 2.获取redisson的RAtomicLong对象，实现原子递增
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);

        // 3.设置过期时间（为避免长期占用内存，设置为2天后过期）
        if(atomicLong.remainTimeToLive() < 0) {
            atomicLong.expire(2, TimeUnit.DAYS);
        }

        // 4.自增并返回新值
        return atomicLong.incrementAndGet();
    }

    /**
     * 生成订单ID
     * @return 订单ID
     */
    public long nextOrderId() {
        return nextId("order");
    }

    /**
     * 生成用户ID
     * @return 用户ID
     */
    public long nextUserId() {
        return nextId("user");
    }

    /**
     * 生成优惠券ID
     * @return 优惠券ID
     */
    public long nextVoucherId() {
        return nextId("voucher");
    }
}
