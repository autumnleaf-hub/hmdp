package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtil;
import com.hmdp.utils.SystemConstants;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCommand;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标来查询
        if (x == null || y == null) {
            Page<Shop> page = lambdaQuery().eq(Shop::getTypeId, typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 如果坐标不为空，则查询附近的商铺
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .limit(end)
                );
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        // 处理分页
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
