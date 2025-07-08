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
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        //一人一单
        Long userId = UserHolder.getUser().getId();
        // 使用Redisson分布式锁，防止超卖
        RLock lock = redissonClient.getLock("lock:userId:" + userId.toString());
        if (!lock.tryLock(1, TimeUnit.SECONDS)) {
            // 获取锁失败，可能是其他线程正在处理
            return Result.fail("请勿重复提交");
        }
        try {
            // 使用事务模板执行数据库操作
            return transactionTemplate.execute(status -> {
                Long count = voucherOrderService.lambdaQuery().eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId)
                        .count();
                if (count > 0) {
                    return Result.fail("您已购买过该秒杀券");
                }

                // 扣除库存
                boolean isSuccess = seckillVoucherService.lambdaUpdate()
                        .setSql("stock = stock - 1") // 直接使用SQL表达式
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0) // 库存大于0才更新
                        .update();
                if (!isSuccess) {
                    return Result.fail("库存不足或秒杀已结束");
                }

                // 生成订单
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setUserId(userId);
                voucherOrder.setVoucherId(voucherId);
                long orderId = redissonIdGenerator.generateSnowflakeId("seckill_order");
                voucherOrder.setId(orderId);
                voucherOrderService.save(voucherOrder);

                return Result.ok(orderId);
            });
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 异步秒杀接口 - 性能优化版本
     */
    @PostMapping("seckill/async/{id}")
    public Result seckillVoucherAsync(@PathVariable("id") Long voucherId) {
        return Result.fail("功能正在开发中...");
    }

    /**
     * 查询订单状态
     */
    @GetMapping("order/status/{id}")
    public Result queryOrderStatus(@PathVariable("id") Long orderId) {
        return Result.fail("功能正在开发中...");
    }
}
