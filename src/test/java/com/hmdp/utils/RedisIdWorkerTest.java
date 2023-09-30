package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 测试并发情况下生成id的性能和值的情况
 */
@SpringBootTest
class RedisIdWorkerTest {
    @Resource
    private RedisIdWorker redisIdWorker;

    // 创建一个线程池，里面有500个线程
    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void TestNextId() throws InterruptedException {
        // 计数器，用于进行线程同步协作
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            // 每个线程来了都生成100个id
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        // 任务提交300次，300*100=30000个id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //等待所有线程完毕
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}