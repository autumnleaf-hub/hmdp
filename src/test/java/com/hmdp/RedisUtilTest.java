package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类测试
 */
@SpringBootTest
@Slf4j
public class RedisUtilTest {

    @Resource
    private RedisUtil redisUtil;

    @Test
    public void testBasicOperations() {
        // 测试基础字符串操作
        String key = "test:string";
        String value = "Hello Redis";
        
        redisUtil.set(key, value);
        String result = redisUtil.get(key);
        log.info("字符串测试 - 设置: {}, 获取: {}", value, result);
        
        // 测试过期时间
        redisUtil.set(key + ":expire", value, 10, TimeUnit.SECONDS);
        Long expireTime = redisUtil.getExpire(key + ":expire");
        log.info("过期时间测试 - 剩余时间: {} 秒", expireTime);
        
        // 清理测试数据
        redisUtil.delete(key);
        redisUtil.delete(key + ":expire");
    }

    @Test
    public void testObjectOperations() {
        // 创建测试对象
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("测试商店");
        shop.setTypeId(1L);
        
        String key = "test:shop:1";
        
        // 测试对象存储
        redisUtil.setObject(key, shop);
        Shop retrievedShop = redisUtil.getObject(key, Shop.class);
        
        log.info("对象测试 - 原始: {}, 获取: {}", shop, retrievedShop);
        
        // 测试带过期时间的对象存储
        redisUtil.setObject(key + ":expire", shop, 30, TimeUnit.SECONDS);
        Shop expireShop = redisUtil.getObject(key + ":expire", Shop.class);
        log.info("带过期时间的对象测试 - 获取: {}", expireShop);
        
        // 清理测试数据
        redisUtil.delete(key);
        redisUtil.delete(key + ":expire");
    }

    @Test
    public void testListOperations() {
        // 创建测试列表
        Shop shop1 = new Shop();
        shop1.setId(1L);
        shop1.setName("商店1");
        
        Shop shop2 = new Shop();
        shop2.setId(2L);
        shop2.setName("商店2");
        
        List<Shop> shopList = Arrays.asList(shop1, shop2);
        String key = "test:shop:list";
        
        // 测试列表存储
        redisUtil.setObject(key, shopList);
        List<Shop> retrievedList = redisUtil.getList(key, Shop.class);
        
        log.info("列表测试 - 原始大小: {}, 获取大小: {}", shopList.size(), 
                retrievedList != null ? retrievedList.size() : 0);
        
        if (retrievedList != null) {
            retrievedList.forEach(shop -> log.info("列表项: {}", shop));
        }
        
        // 清理测试数据
        redisUtil.delete(key);
    }

    @Test
    public void testIncrementOperations() {
        String key = "test:counter";
        
        // 测试递增操作
        Long count1 = redisUtil.increment(key);
        Long count2 = redisUtil.increment(key, 5);
        Long count3 = redisUtil.decrement(key, 2);
        
        log.info("计数器测试 - 第一次递增: {}, 递增5: {}, 递减2: {}", count1, count2, count3);
        
        // 清理测试数据
        redisUtil.delete(key);
    }

    @Test
    public void testKeyOperations() {
        // 测试 key 操作
        String pattern = "test:key:*";
        
        // 创建一些测试 key
        redisUtil.set("test:key:1", "value1");
        redisUtil.set("test:key:2", "value2");
        redisUtil.set("test:key:3", "value3");
        
        // 查找匹配的 key
        var keys = redisUtil.keys(pattern);
        log.info("匹配的 key 数量: {}", keys != null ? keys.size() : 0);
        
        // 批量删除
        if (keys != null && !keys.isEmpty()) {
            Long deletedCount = redisUtil.delete(keys);
            log.info("批量删除 key 数量: {}", deletedCount);
        }
    }
}
