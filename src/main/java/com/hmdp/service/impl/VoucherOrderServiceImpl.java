package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 阻塞队列
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    IVoucherOrderService proxy;


    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从对列中去拿信息
    private class VoucherOrderHandler implements Runnable, com.hmdp.service.impl.VoucherOrderHandler {
        @Override
        public void run() {
            while (true) {
                try {
                    // 之前：1.获取阻塞队列中的订单信息
                    // VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    // handleVoucherOrder(voucherOrder);

                    // 现在: 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据   string就是消息id
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 把map转成order对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    // 处理异常消息
                    handlePendingList();
                }
            }
        }


        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    // 注意这里是0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1), StreamOffset.create("stream.orders", ReadOffset.from("0")));
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                }
            }
        }

    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户，注意是从voucherOrder中取，因为现在是在多线程中
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 4.判断是否获得锁成功
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId),
                String.valueOf(orderId));
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // VoucherOrder voucherOrder = new VoucherOrder();
        // voucherOrder.setId(orderId);
        // // 2.4.用户id
        // voucherOrder.setUserId(userId);
        // // 2.5.代金券id
        // voucherOrder.setVoucherId(voucherId);
        // // 2.6.放入阻塞队列
        // orderTasks.add(voucherOrder);
        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过了");
            return;
        }

        // 6.扣减库存【乐观锁：
        // 6.1 只要扣减库存时的库存和之前查的一样，就意味着没有人在中间修改过库存，那么就是安全的  ==》成功几率太小
        // 6.2 只要 > 0 就可以扣减成功，解决超卖问题】
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {   // 扣减失败
            log.error("库存不足");
            return;
        }
        save(voucherOrder);

    }


    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //
    //     // 判断是否在秒杀时间内
    //     if (voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //         return Result.fail("不在秒杀时间内");
    //     }
    //
    //     // 判断是否还有库存
    //     if (voucher.getStock() < 1) {
    //         return Result.fail("库存不足");
    //     }
    //
    //     Long userId = UserHolder.getUser().getId();
    //     // 之前的：没有考虑集群模式下的锁问题
    //     // 通过userId控制锁的粒度，只有相同用户才会加锁
    //     // synchronized是java内置的一个线程同步关键字，可以写在需要同步的对象、方法或者特定的代码块中
    //     // intern()方法是将字符串放入常量池中，这样相同的字符串就会指向同一个对象，从而实现锁的粒度控制
    //     synchronized (userId.toString().intern()) {
    //     // 如果直接使用this调用方法，调用的是非代理对象，但是事务是靠代理对象生效的，所以我们要拿到代理对象，走代理对象的方法，才能实现事务控制
    //         // 通过AopContext.currentProxy()获取代理对象，从而实现事务控制
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     }
    //
    //     // 完善：考虑集群模式下的锁问题
    //     // 创建锁对象
    //     // 这里修改成了redisson的分布式锁
    //     // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //     RLock lock = redissonClient.getLock("lock:order:" + userId);
    //
    //     //获取锁对象
    //     boolean isLock = lock.tryLock();
    //     //加锁失败
    //     if (!isLock) {
    //         return Result.fail("之前的下单逻辑还在处理/不允许重复下单");
    //     }
    //     // 这里就是为了调用createVoucherOrder方法，但是要考虑到事务的问题，所以要通过代理对象来调用
    //     try {
    //         //获取代理对象(事务)
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     } finally {
    //         lock.unlock();
    //     }
    // }

    // public Result createVoucherOrder(Long voucherId) {
    //     // 一人一单逻辑
    //     Long userId = UserHolder.getUser().getId();
    //
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //     // 判断是否存在
    //     if (count > 0) {
    //         return Result.fail("用户已经购买过一次！");
    //     }
    //
    //     // 减库存
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock-1")
    //             .eq("voucher_id", voucherId)
    //             // 乐观锁解决超卖问题
    //             // .eq("stock", voucher.getStock())
    //             .gt("stock", 0)
    //             .update();
    //
    //     if (!success) {
    //         return Result.fail("减库存失败");
    //     }
    //
    //     // 创建订单：保存订单信息到数据库中
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //
    //     // 生成订单id、用户id、代金券id
    //     long orderId = redisIdWorker.nextId("order");
    //     voucherOrder.setId(orderId);
    //     voucherOrder.setUserId(UserHolder.getUser().getId());
    //     voucherOrder.setVoucherId(voucherId);
    //
    //     save(voucherOrder);
    //     return Result.ok(orderId);
    // }

}
