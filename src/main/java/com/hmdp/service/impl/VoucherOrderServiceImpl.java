package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 判断是否在秒杀时间内
        if (voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("不在秒杀时间内");
        }

        // 判断是否还有库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        // 之前的：没有考虑集群模式下的锁问题
        // 通过userId控制锁的粒度，只有相同用户才会加锁
        // synchronized是java内置的一个线程同步关键字，可以卸载需要同步的对象、方法或者特定的代码块中
        // intern()方法是将字符串放入常量池中，这样相同的字符串就会指向同一个对象，从而实现锁的粒度控制
        // synchronized (userId.toString().intern()) {
        //     // 通过AopContext.currentProxy()获取代理对象，从而实现事务控制
        //     IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //     return proxy.createVoucherOrder(voucherId);
        // }

        // 完善：考虑集群模式下的锁问题
        // 创建锁对象
        // 这里修改成了redisson的分布式锁
        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //获取锁对象
        boolean isLock = lock.tryLock();
        //加锁失败
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        // 这里就是为了调用createVoucherOrder方法，但是要考虑到事务的问题，所以要通过代理对象来调用
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }

    public Result createVoucherOrder(Long voucherId) {
        // 一人一单逻辑
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过一次！");
        }

        // 减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                // 乐观锁解决超卖问题
                // .eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("减库存失败");
        }

        // 创建订单：保存订单信息到数据库中
        VoucherOrder voucherOrder = new VoucherOrder();

        // 生成订单id、用户id、代金券id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        return Result.ok(orderId);
    }

}
