package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.constant.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.constant.RedisConstants.LOCK_SHOP_TTL;

@Slf4j
@Component
public class CacheCilent {

    private final StringRedisTemplate stringRedisTemplate;

    //  线性池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheCilent(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     *
     * @param key   redis键
     * @param value redis值
     * @param time  缓存时间
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        // 注意这里要把value转为string类型
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param key   redis键
     * @param value redis值
     * @param time  缓存时间
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 注意转second
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix  键前缀
     * @param id         就id啦
     * @param type       要转换的数据类型
     * @param dbFallback 咩？
     * @param time       时间
     * @param unit       时间单位
     * @param <R>        数据类型
     * @param <ID>       id类型
     */

    // 返回值不确定 —— 使用泛型（先定义泛型Class<R> type，再返回类型<R>R）
    // id也不确定 —— 还是泛型，用ID，泛型类型定义改成<R,ID>R
    // 查数据库的逻辑不确定 —— 用Function<ID, R> :ID是入参，R是返回值
    // 过期时间也不要写死 —— 用Long time, TimeUnit unit
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //查询redis，若存在则转换成对象后返回
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);

        //这⾥判断的是Json是否真的有值，不包括空值
        if (StringUtils.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, type);
        }

        // 判断缓存是否命中(命中的是否是空值)。
        // 如果isNotBlank ＋ !=null，说明命中，之前就请求过了且redis设为了“”，这种情况也不要再请求redis了，直接返回错误
        if (Json != null) {
            return null;
        }
        //不存在则查询数据库，然后转成以json串存⼊redis后，返回
        R r = dbFallback.apply(id);
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, unit);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }


    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用逻辑过期的方式解决缓存击穿问题
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //查询redis，这里的shopJson是(Object)RedisData类型的
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);

        //未命中，说明不是热点key
        if (StringUtils.isBlank(Json)) {
            return null;
        }

        // 命中的话再判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 未过期直接返回shop
        if (LocalDateTime.now().isBefore(expireTime)) {
            return r;
        }

        //过期了就重建缓存：先获取锁，再开个独立线程处理
        String lockKey = keyPrefix + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 模拟重建延迟  saveShop2Redis
                    // 1. 查数据库
                    R r1 = dbFallback.apply(id);
                    // 2. 带逻辑过期地写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 返回旧数据
        return r;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用互斥锁的方式解决缓存击穿问题
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, String lockKeyPrefix, Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        //查询redis，若存在则转换成对象后返回
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //这⾥判断的是shopJson是否真的有值，不包括空值
        if (StringUtils.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 判断缓存是否命中(命中的是否是空值)。
        // 如果isNotBlank ＋ !=null，说明命中，之前就请求过了且redis设为了“”，这种情况也不要再请求redis了，直接返回错误
        if (json != null) {
            return null;
        }
        // 未命中，进行缓存穿透处理
        // 先加锁，防止缓存穿透
        String lockKey = lockKeyPrefix + id;
        R r1;
        try {
            boolean lock = tryLock(lockKey);
            while (!lock) {
                // 获取锁失败，偷偷睡一觉，再重新查询
                TimeUnit.MILLISECONDS.sleep(50);
                lock = tryLock(lockKey);
            }
            // DoubleCheck(因为此时有可能别的线程已经重新构建好缓存)
            json = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json)) {
                r1 = JSONUtil.toBean(json, type);
                return r1;
            }

            // 模拟重建延迟
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            //不存在则查询数据库，然后转成以json串存⼊redis后，返回
            r1 = JSONUtil.toBean(json, type);
            if (r1 == null) {
                // 将空值写入redis,解决缓存穿透问题
                stringRedisTemplate.opsForValue().set(key, "", 10, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r1), time, unit);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }

        return r1;
    }

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

}
