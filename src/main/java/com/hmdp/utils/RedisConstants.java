package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

public class RedisConstants {
    // 验证码
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final TimeUnit LOGIN_CODE_TTL_TIMEUNIT = TimeUnit.MINUTES;

    // 登录用户
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 7L;
    public static final TimeUnit LOGIN_USER_TTL_TIMEUNIT = TimeUnit.DAYS;

    // 空值缓存
    public static final Long CACHE_NULL_TTL = 2L;
    public static final TimeUnit CACHE_NULL_TTL_TIMEUNIT = TimeUnit.MINUTES;

    // 商铺相关常量
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final TimeUnit CACHE_SHOP_TTL_TIMEUNIT = TimeUnit.MINUTES;
    public static final String CACHE_SHOP_KEY = "cache:shop:";


    public static final Long CACHE_SHOP_TYPE_TTL = 30L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopTypes";

    // 商户锁
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final TimeUnit LOCK_SHOP_TTL_TIMEUNIT = TimeUnit.SECONDS;

    // 逻辑删除
    public static final String CACHE_REDIS_DATA_KEY = "cache:redisData:";

    // 秒杀优惠券
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:"; // 新增：秒杀订单KEY

    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
