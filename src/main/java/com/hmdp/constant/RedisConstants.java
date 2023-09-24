package com.hmdp.constant;

public class RedisConstants {
    // 登录用到的Key
    public static final String LOGIN_USER_KEY = "login_user_";
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final Long LOGIN_USER_TTL = 3600L;

    // 商品查询⽤到的key
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;
    //避免缓存穿透
    public static final Long CACHE_NULL_TTL = 2L;
    //避免缓存击穿
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    // Blog点赞用到的Key
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
