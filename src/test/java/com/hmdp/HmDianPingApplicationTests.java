package com.hmdp;

import com.hmdp.utils.GlobalRedisIdGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Resource
    GlobalRedisIdGenerator globalRedisIdGenerator;

    @Resource
    RedissonClient redissonClient;

    ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    void testIdGenerator() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(100);
        Long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    System.out.println(globalRedisIdGenerator.nextId("test"));
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        System.out.println("耗时：" + (System.currentTimeMillis() - start));
    }

    @Test
    void testRedisson(){
        CountDownLatch countDownLatch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                RLock lock = redissonClient.getLock("test");
                boolean isLocked = false;
                try {
                    while (!isLocked) {
                        isLocked = lock.tryLock(10, 1000, TimeUnit.MILLISECONDS);
                        if (!isLocked) {
                            Thread.sleep(50); // 自旋等待100毫秒后再尝试
                        }
                    }
                    // 获取锁成功后执行业务逻辑
                    // TODO: 在此处添加业务代码
                    System.out.println("获取锁成功，开始执行业务逻辑。count: " + countDownLatch.getCount());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (isLocked) {
                        lock.unlock();
                    }
                    countDownLatch.countDown();
                }
            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    RLock lock ;
    @BeforeEach
    void init(){
        lock = redissonClient.getLock("reentrant");
    }

    @Test
    void method1() throws InterruptedException {
        boolean isLocked = lock.tryLock(1, TimeUnit.SECONDS);
        if (!isLocked){
            log.error("获取锁失败, 1");
            return;
        }
        try {
            log.info("获取锁成功, 1");
            method2();
        } finally {
            log.error("释放锁, 1");
            lock.unlock();
        }
    }

    private void method2() {
        boolean  isLocked = lock.tryLock();
        if (!isLocked){
            log.error("获取锁失败, 2");
            return;
        }
        try {
            log.info("获取锁成功, 2");
        } finally {
            log.error("释放锁, 2");
            lock.unlock();
        }
    }

}
