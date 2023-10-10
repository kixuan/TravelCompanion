package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.LOCK_KEY_PREFIX;

public class SimpleRedisLock implements ILock {

    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = LOCK_KEY_PREFIX;
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 提前在静态代码块中加载lua脚本，提高性能
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标示，因为我们要区分到底是哪个线程拿到了这个锁，方便之后unLock的时候进行判断
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }


    @Override
    public void unlock() {
        // 解决误删问题
        // // 获取线程标示
        // String threadId = ID_PREFIX + Thread.currentThread().getId();
        // // 获取锁中的标示
        // String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // // 判断标示是否一致
        // if(threadId.equals(id)) {
        //     stringRedisTemplate.delete(KEY_PREFIX + name);

        // 调用lua脚本解决误删问题，确保原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                // 字符串转集合
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }
}

