package com.hmdp.utils;

import com.hmdp.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1620000000000L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    // spring推荐构造器注入，其实功能好像是一样的
    // 因为这里的stringRedisTemplate是通过构造函数注入的，而不是@Resouce注入的，所以需要手动写一个构造函数
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId (String keyPrefix){
        // 1. 获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 获取当前序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        if (count == null) {
            return Result.fail("获取序列号失败").getTotal();
        }
        // 3. 拼接
        return timestamp<< COUNT_BITS | count;

        // q： 是哪一行代码把redis写入的
        // a： 2.2 自增长 stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
    }

}
