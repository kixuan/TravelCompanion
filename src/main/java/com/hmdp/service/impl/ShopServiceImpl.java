package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheCilent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.constant.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private CacheCilent cacheCilent;


    /**
     * @Description: 通过id查询
     * @Param: [id]
     * @return: com.hmdp.dto.Result
     */

    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = cacheCilent
        //         .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        // 解决缓存击穿——互斥锁


        // 解决缓存击穿——逻辑过期
        Shop shop = cacheCilent.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }



    // 往redis加⼊带有逻辑过期的数据。
    // public void saveShop2Redis(Long id, Long expireSecond) throws InterruptedException {
    //     Shop shop = getById(id);
    //     System.out.println("shop = " + shop);
    //     RedisData redisData = new RedisData();
    //     redisData.setData(shop);
    //     redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
    //
    //     // 模拟重建缓存当延迟
    //     try {
    //         TimeUnit.MILLISECONDS.sleep(200);
    //     } catch (InterruptedException e) {
    //         throw new RuntimeException(e);
    //     }
    //     System.out.println("缓存到redis中、、、、");
    //     stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    //     System.out.println("缓存到redis中成功、、、、");
    // }


    @Override
    @Transactional //保证原⼦性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //先更新数据库，再删除缓存
        shopMapper.updateById(shop);
        return Result.ok();
    }
}
