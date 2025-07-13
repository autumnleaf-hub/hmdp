package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-10 18:17
 */

@SpringBootTest
public class CommonTest {

    @Resource
    RedisIdWorker redisIdWorker;
    ExecutorService es = Executors.newFixedThreadPool(100);

    @Test
    void testAvailability(){
        System.out.println(redisIdWorker.nextId("test"));
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Resource
    RedissonClient redissonClient;


    @Test
    void testRedisson() throws InterruptedException {
        RLock lock = redissonClient.getLock("testLock");
        if (lock.tryLock(1, 10, TimeUnit.SECONDS)) {
            try {
                // 执行需要加锁的操作
                System.out.println("Lock acquired, executing critical section.");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
                System.out.println("Lock released.");
            }
        } else {
            System.out.println("Could not acquire lock, try again later.");
        }
    }


    RLock lock;
    @Test
    void testReentrantRedissonLock() throws InterruptedException {
        lock = redissonClient.getLock("testReentrantLock");
        boolean success = lock.tryLock(10,100,TimeUnit.SECONDS);
        if (success) {
            try {
                System.out.println("获取锁，1");
                method2();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
                System.out.println("释放锁，1");
            }
        } else {
            System.out.println("获取锁失败，1");
        }
    }

    private void method2() throws InterruptedException {
        boolean success = lock.tryLock(10,100,TimeUnit.SECONDS);
        if (success) {
            try {
                System.out.println("获取锁，2");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
                System.out.println("释放锁，2");
            }
        } else {
            System.out.println("获取锁失败，2");
        }
    }


}































