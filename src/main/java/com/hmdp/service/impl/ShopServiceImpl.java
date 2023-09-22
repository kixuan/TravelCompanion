package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    /**
     * @Description: 通过id查询
     * @Param: [id]
     * @return: com.hmdp.dto.Result
     */
    @Override
    // 使用互斥锁解决缓存击穿
    public Result queryById(Long id) {
        //查询redis，若存在则转换成对象后返回
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //这⾥判断的是shopJson是否真的有值，不包括空值
        if (StringUtils.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //更新缓存时间
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        }

        // 判断缓存是否命中(命中的是否是空值)。
        // 如果isNotBlank ＋ !=null，说明命中，之前就请求过了且redis设为了“”，这种情况也不要再请求redis了，直接返回错误
        if (shopJson != null) {
            return Result.fail("redis中为“”，店铺信息不存在！");
        }
        // 未命中，进行缓存穿透处理
        // 先加锁，防止缓存穿透
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean lock = tryLock(lockKey);
            while (!lock) {
                // 获取锁失败，偷偷睡一觉，再重新查询
                TimeUnit.MILLISECONDS.sleep(50);
                lock = tryLock(lockKey);
            }
            // DoubleCheck(因为此时有可能别的线程已经重新构建好缓存)
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(shopJson)) {
                shop = JSONUtil.toBean(shopJson, Shop.class);
                stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return Result.ok(shop);
            }

            // 模拟重建延迟
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            //不存在则查询数据库，然后转成以json串存⼊redis后，返回
            shop = shopMapper.selectById(id);
            if (shop == null) {
                // 将空值写入redis,解决缓存穿透问题
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在！");
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }

        return Result.ok(shop);
    }

    //获取锁
    private boolean tryLock(String lockKey) {
        // ⾸先尝试获取锁,获取不到返回false
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 不直接返回Boolean类型，避免⾃动拆箱时出现空指针异常。
        // setIfAbsent内部是long转boolean再转Boolean，可能会出现空指针异常的
        // 条件表达式，只有满足不为null和为true时才返回true
        // return flag != null && flag;
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
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
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
