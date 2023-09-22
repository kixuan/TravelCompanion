package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheCilent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class ShopServiceImplTest {

    @Resource
    private CacheCilent cacheCilent;

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Test
        // 利用单元测试进行缓存预热
    void setWithLogicalExpire() throws InterruptedException {
        Shop shop = shopServiceImpl.getById(1L);
        cacheCilent.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }
}