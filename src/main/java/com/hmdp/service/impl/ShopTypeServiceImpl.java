package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    ShopTypeMapper shopTypeMapper;

    @Resource
    RedisUtil redisUtil;


    /**
     * 为查询添加 redis 缓存，查询结果按照 sort 升序排列
     * @return
     */
    @Override
    public List<ShopType> cacheList() {
        List<ShopType> res = null;
        if (redisUtil.hasKey(RedisConstants.CACHE_SHOP_TYPE_KEY)) {
            res = redisUtil.getList(RedisConstants.CACHE_SHOP_TYPE_KEY, ShopType.class);
        } else {
            res = shopTypeMapper.selectList(Wrappers.<ShopType>lambdaQuery().orderByAsc(ShopType::getSort));
            // cache
            if (!res.isEmpty()) {
                redisUtil.setObject(RedisConstants.CACHE_SHOP_TYPE_KEY, res, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
        }

        return res;
    }
}
