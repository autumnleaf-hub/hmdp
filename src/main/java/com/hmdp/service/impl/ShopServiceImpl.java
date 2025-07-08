package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 通过互斥锁解决缓存击穿的问题
     * @param id
     * @return 返回找到的对象，如果没有则返回 null
     */
    @Override
    public Shop cachedGetById(Long id) {
        String shopRedisKey = RedisConstants.CACHE_SHOP_KEY + id;
        Shop res = null;
        while (true) {
            if (redisUtil.hasKey(shopRedisKey)) {
                res = redisUtil.getObject(shopRedisKey, Shop.class);
                break;
            } else {
                // 缓存未命中
                // 尝试获取锁
                if(locks.computeIfAbsent(id, a -> new ReentrantLock()).tryLock()) {
                    // success
                    try {
                        res = shopMapper.selectById(id);
                        redisUtil.setObject(shopRedisKey, res, (long)(RedisConstants.CACHE_SHOP_TTL * RandomUtil.randomDouble(0.6, 1)), TimeUnit.MINUTES);
                    } finally {
                        locks.get(id).unlock();
                    }
                    break;
                } else {
                    // fail
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return res;
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





























