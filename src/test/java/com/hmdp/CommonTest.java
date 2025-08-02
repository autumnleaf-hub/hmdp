package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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

    @Component
    public static class Person{
        @Resource
        Person person;
    }


    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    void testStream(){
        List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                Consumer.from("g1", "c1"),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
        );
        System.out.println(list);
    }

    @Resource
    IShopService shopService;

    @Test
    void loadShopData() {
        // 获取所有商铺
        List<Shop> shops = shopService.list();
        // 按类型给商铺分类
        Map<Long, List<Shop>> type2Shops = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 遍历每个类型的商铺列表，将其添加到Redis的Geo集合中
        for (Map.Entry<Long, List<Shop>> entry : type2Shops.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();
            String key = SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = entry.getValue()
                    .stream()
                    .map(shop -> new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                    ))
                    .toList();
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHLL() {
        String key= "hll";

        ArrayList<String> batch = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            batch.add(String.valueOf(i));

            if (batch.size() == 10000) {
                stringRedisTemplate.opsForHyperLogLog().add(key, batch.toArray(new String[0]));
                batch.clear();
            }
        }

        System.out.println("HyperLogLog count: " + stringRedisTemplate.opsForHyperLogLog().size(key));
    }

    Long time;

    @BeforeEach
    void init() {
        time = System.currentTimeMillis();
    }

    @AfterEach
    void end() {
        long endTime = System.currentTimeMillis();
        System.out.println("测试耗时：" + (endTime - time) + "毫秒");
    }


}































