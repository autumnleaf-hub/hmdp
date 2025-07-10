package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    ShopMapper shopMapper;

    @Resource
    RedisUtil redisUtil;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 通过互斥锁解决缓存击穿的问题
     * @param id
     * @return 返回找到的对象，如果没有则返回 null
     */
    @Override
    public Shop cachedGetById(Long id) {
        //return queryWithMutex(id);
        return queryWithLogicDelete(id);
    }


    private boolean tryLock(String key) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                RedisConstants.LOCK_SHOP_TTL,
                RedisConstants.LOCK_SHOP_TTL_TIMEUNIT);
        return Boolean.TRUE.equals(b);
    }

    private void unlock(String key) {
        redisUtil.delete(key);
    }

    /**
     * 通过互斥锁解决缓存击穿的问题
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String shopRedisKey = RedisConstants.CACHE_SHOP_KEY + id;

        // 缓存命中
        if (redisUtil.hasKey(shopRedisKey)) {
            return redisUtil.getObject(shopRedisKey, Shop.class);
        }

        // 缓存未命中，尝试获取锁
        String redisLockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            if (tryLock(redisLockKey)) {
                Shop shop = shopMapper.selectById(id);
                if (shop == null) {
                    // 如果数据库中没有该商铺，则设置空值缓存，防止缓存穿透
                    redisUtil.setObject(shopRedisKey,
                            shop,
                            RedisConstants.CACHE_NULL_TTL,
                            RedisConstants.CACHE_NULL_TTL_TIMEUNIT);
                } else {
                    redisUtil.setObject(shopRedisKey,
                            shop,
                            (long)(RedisConstants.CACHE_SHOP_TTL * RandomUtil.randomDouble(0.6, 1)),
                            RedisConstants.CACHE_SHOP_TTL_TIMEUNIT);
                }
                return shop;
            } else {
                Thread.sleep(50);
                return queryWithMutex(id); // 递归调用，直到获取到锁d
            }
        } catch (Exception e) {
            log.error("获取商铺缓存失败，id: {}, 错误信息: {}", id, e.getMessage());
            return null;
        } finally {
            unlock(redisLockKey);
        }
    }


    /**
     * 通过逻辑删除解决缓存击穿的问题
     * @param id
     * @return
     */
    public Shop queryWithLogicDelete(Long id) {
        String redisDataKey = RedisConstants.CACHE_REDIS_DATA_KEY + RedisConstants.CACHE_SHOP_KEY + id;
        RedisData targetShop = null;

        // 缓存命中
        if (redisUtil.hasKey(redisDataKey)) {
            try {
                targetShop = redisUtil.getObject(redisDataKey, RedisData.class);
            } catch (Exception e) {
                log.error("获取商铺缓存失败，id: {}, 错误信息: {}", id, e.getMessage());
                return null;
            }
        }

        // 检查过期时间
        if (targetShop == null || targetShop.getExpireTime().isBefore(LocalDateTime.now())) {
            String redisLockKey = RedisConstants.LOCK_SHOP_KEY + id;
            if (tryLock(redisLockKey)) {
                try {
                    CACHE_REBUILD_EXECUTOR.submit(() -> {
                        Shop shop = shopMapper.selectById(id);
                        if (shop == null) {
                            // 如果数据库中没有该商铺，则设置空值缓存，防止缓存穿透
                            redisUtil.setObject(redisDataKey,
                                    shop,
                                    RedisConstants.CACHE_NULL_TTL,
                                    RedisConstants.CACHE_NULL_TTL_TIMEUNIT);
                        } else {
                            RedisData redisData = new RedisData();
                            redisData.setData(shop);
                            redisData.setExpireTime(LocalDateTime.now().plus(RedisConstants.CACHE_SHOP_TTL,
                                    RedisConstants.CACHE_SHOP_TTL_TIMEUNIT.toChronoUnit()));
                            redisUtil.setObject(redisDataKey, redisData);
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(redisLockKey);
                }

            }
        }

        return targetShop == null ? null : (Shop) targetShop.getData();
    }



    @Override
    @Transactional
    public void cachedUpdateById(Shop shop) {
        if (shop.getId() == null) {
            return;
        }
        String shopKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        Shop cachedShop = redisUtil.getObject(shopKey, Shop.class);
        try {
            shopMapper.updateById(shop);
            redisUtil.delete(shopKey);
        } catch (Exception e) {
            if (cachedShop != null) redisUtil.setObject(shopKey, cachedShop);
            throw new RuntimeException(e);
        }
    }
}





























