package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class ShopServiceImplTest {

    @Resource
    private ShopServiceImpl shopServiceImpl;
    @Test
    // 利用单元测试进行缓存预热
    void saveShop2Redis() throws InterruptedException {
        System.out.println("开始预热缓存 ...");
        shopServiceImpl.saveShop2Redis(1L, 10L);
    }
}