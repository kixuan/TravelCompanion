package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheCilent;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;

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

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * @Description: 通过id查询
     * @Param: [id]
     * @return: com.hmdp.dto.Result
     */

    public Result queryById(Long id){
        // 解决缓存穿透
        Shop shop = cacheCilent
                .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    // 使用互斥锁解决缓存击穿
    // @Override
    // public Result queryById(Long id) {
    //     //查询redis，若存在则转换成对象后返回
    //     String key = CACHE_SHOP_KEY + id;
    //     String shopJson = stringRedisTemplate.opsForValue().get(key);
    //
    //     //这⾥判断的是shopJson是否真的有值，不包括空值
    //     if (StringUtils.isNotBlank(shopJson)) {
    //         Shop shop = JSONUtil.toBean(shopJson, Shop.class);
    //         //更新缓存时间
    //         stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //         return Result.ok(shop);
    //     }
    //
    //     // 判断缓存是否命中(命中的是否是空值)。
    //     // 如果isNotBlank ＋ !=null，说明命中，之前就请求过了且redis设为了“”，这种情况也不要再请求redis了，直接返回错误
    //     if (shopJson != null) {
    //         return Result.fail("redis中为“”，店铺信息不存在！");
    //     }
    //     // 未命中，进行缓存穿透处理
    //     // 先加锁，防止缓存穿透
    //     String lockKey = LOCK_SHOP_KEY + id;
    //     Shop shop;
    //     try {
    //         boolean lock = tryLock(lockKey);
    //         while (!lock) {
    //             // 获取锁失败，偷偷睡一觉，再重新查询
    //             TimeUnit.MILLISECONDS.sleep(50);
    //             lock = tryLock(lockKey);
    //         }
    //         // DoubleCheck(因为此时有可能别的线程已经重新构建好缓存)
    //         shopJson = stringRedisTemplate.opsForValue().get(key);
    //         if (StringUtils.isNotBlank(shopJson)) {
    //             shop = JSONUtil.toBean(shopJson, Shop.class);
    //             stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //             return Result.ok(shop);
    //         }
    //
    //         // 模拟重建延迟
    //         try {
    //             TimeUnit.MILLISECONDS.sleep(100);
    //         } catch (InterruptedException e) {
    //             throw new RuntimeException(e);
    //         }
    //
    //         //不存在则查询数据库，然后转成以json串存⼊redis后，返回
    //         shop = shopMapper.selectById(id);
    //         if (shop == null) {
    //             // 将空值写入redis,解决缓存穿透问题
    //             stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
    //             return Result.fail("店铺不存在！");
    //         }
    //         stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //     } catch (InterruptedException e) {
    //         throw new RuntimeException(e);
    //     } finally {
    //         //释放锁
    //         unLock(lockKey);
    //     }
    //
    //     return Result.ok(shop);
    // }


    // @Override
    // 使用逻辑过期解决缓存击穿
    // 注意这个的前提是需要的热点key都已经被存到redis里面了，所以判断的逻辑需要进行改变
    // public Result queryById(Long id) {
    //     //查询redis，这里的shopJson是(Object)RedisData类型的
    //     String key = CACHE_SHOP_KEY + id;
    //     String shopJson = stringRedisTemplate.opsForValue().get(key);
    //
    //     //未命中，说明不是热点key
    //     if (StringUtils.isBlank(shopJson)) {
    //         return null;
    //     }
    //
    //     // 命中的话再判断是否逻辑过期
    //     RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    //     Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    //     LocalDateTime expireTime = redisData.getExpireTime();
    //     // 未过期直接返回shop
    //     if (LocalDateTime.now().isBefore(expireTime)) {
    //         return Result.ok(shop);
    //     }
    //
    //     //过期了就重建缓存：先获取锁，再开个独立线程处理
    //     String lockKey = LOCK_SHOP_KEY + id;
    //     boolean lock = tryLock(lockKey);
    //     if (lock) {
    //         CACHE_REBUILD_EXECUTOR.submit(() -> {
    //             try {
    //                 // 模拟重建延迟
    //                 this.saveShop2Redis(id, 20L);
    //             } catch (Exception e) {
    //                 throw new RuntimeException(e);
    //             } finally {
    //                 unLock(lockKey);
    //             }
    //         });
    //     }
    //     // 返回旧数据
    //     return Result.ok(shop);
    // }

    //获取锁
    private boolean tryLock(String lockKey) {
        // ⾸先尝试获取锁,获取不到返回false
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 不直接返回Boolean类型，避免⾃动拆箱时出现空指针异常。setIfAbsent内部是long转boolean再转Boolean，可能会出现空指针异常的
        // 条件表达式，只有满足不为null和为true时才返回true
        // return flag != null && flag;
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    // 往redis加⼊带有逻辑过期的数据。
    public void saveShop2Redis(Long id, Long expireSecond) throws InterruptedException {
        Shop shop = getById(id);
        System.out.println("shop = " + shop);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));

        // 模拟重建缓存当延迟
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("缓存到redis中、、、、");
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
        System.out.println("缓存到redis中成功、、、、");
    }


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
