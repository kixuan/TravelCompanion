package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * 优惠券
 * @author kixuan
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 秒杀结果
     */
    Result seckillVoucher(Long voucherId);


    /**
     * 创建优惠券订单
     * @param voucherOrder 优惠券订单
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
