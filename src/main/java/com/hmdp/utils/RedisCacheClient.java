package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 利用 redis 作为缓存，缓解数据库压力。同时实现了实现了如下安全防护功能：
 * 缓存击穿：多次无效查询。-》 缓存短生命周期空对象
 * 缓存雪崩：热点数据同时过期/redis宕机。 -》 随机化TTL / redis 集群（未实现）
 * 缓存击穿：高并发访问、且重建困难的数据失效。  -》 使用互斥锁 / 逻辑过期
 *
 * 依赖 RedisUtil、RedisData 实现。
 * @author fzy
 * @version 1.0
 * 创建时间：2025-05-28 15:39
 */

@Component
public class RedisCacheClient {

    @Resource
    RedisUtil redisUtil;

    /**
     * 通过互斥锁的方式
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFuc
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <T>
     */
    public <R,T> R getByIdWithMutex(String keyPrefix, T id, Class<R> type, Function<T, R> dbFuc, Long time, TimeUnit unit) {
        // 从redis中查
        // 尝试获取锁
        // 获取成功，需要再次查redis，然后
        return null;
    }
}
