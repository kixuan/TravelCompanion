package com.hmdp.service.impl;

import com.hmdp.entity.Voucher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@SpringBootTest
class VoucherServiceImplTest {

    @Resource
    private VoucherServiceImpl voucherService;

    @Test
    void addSeckillVoucher() {
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("200元代金券");
        voucher.setSubTitle("200代150");
        voucher.setRules("只能在迅迅子开心的时候用");
        voucher.setPayValue(200L);
        voucher.setActualValue(150L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.now());
        voucher.setEndTime(LocalDateTime.now().plusSeconds(1000000));
        voucher.setCreateTime(LocalDateTime.now());
        voucher.setUpdateTime(LocalDateTime.now());

        voucherService.addSeckillVoucher(voucher);
    }
}