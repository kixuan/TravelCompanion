package com.hmdp.utils;

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

@Slf4j
@Component
public class CacheCilent {

    private final StringRedisTemplate stringRedisTemplate;

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
     * 根据指定的key查询缓存，并反序列化为指定类型，利用互斥锁的方式解决缓存击穿问题
     */

    // public <R, ID> R queryWithLogicalExpire(
    //         String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
    // }


}
