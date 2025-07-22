package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedissonIdGenerator;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedissonIdGenerator redissonIdGenerator;

    @Resource
    private TransactionTemplate transactionTemplate;

    //@Resource
    //private IVoucherOrderAsyncService voucherOrderAsyncService;

    /**
     * 同步秒杀接口 - 传统实现
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 异步秒杀接口 - 性能优化版本
     */
    @PostMapping("seckill/async/{id}")
    public Result seckillVoucherAsync(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucherAsync(voucherId);
    }

    /**
     * 查询订单状态
     * TODO 暂时用作获取订单全部信息接口
     */
    @GetMapping("order/status/{id}")
    public Result queryOrderStatus(@PathVariable("id") Long orderId) {
        return Result.ok(voucherOrderService.getById(orderId));
    }
}
