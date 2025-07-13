package com.hmdp.utils;

import com.hmdp.interfaces.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 借助 Redis 实现简单的分布式锁
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-12 16:17
 */

public class SimpleRedisLock implements ILock {
    String LOCK_PREFIX = "lock:"; // 所有分布式锁的公共前缀
    // 锁的前缀
    String prefix;
    StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> defaultRedisScript;
    static {
        defaultRedisScript = new DefaultRedisScript<>();
        defaultRedisScript.setLocation(new ClassPathResource("lua/unlock.lua"));
        defaultRedisScript.setResultType(Long.class);
    }

    public SimpleRedisLock(String prefix, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.prefix = prefix;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的自动过期时间，单位为秒
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + prefix, String.valueOf(threadId), timeoutSec, TimeUnit.SECONDS));
    }

    @Override
    public boolean unlock() {
        // 只有持有锁的线程才能释放锁
        // java 代码无法保证 redis 操作的原子性，所以需要使用 Lua 脚本来保证原子性
        //if (String.valueOf(Thread.currentThread().getId()).equals(stringRedisTemplate.opsForValue().get(LOCK_PREFIX + prefix))) {
        //    return  stringRedisTemplate.delete(LOCK_PREFIX + prefix);
        //}
        //return false;

        // 使用 Lua 脚本来保证原子性
        Long result = stringRedisTemplate.execute(
                defaultRedisScript,
                List.of(LOCK_PREFIX + prefix), // 锁的 key
                Thread.currentThread().getId() + "" // 传入当前线程的 ID
        );
        return result == 1L;
    }
}
