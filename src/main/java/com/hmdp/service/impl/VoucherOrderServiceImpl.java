package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    TransactionTemplate transactionTemplate;

    @Resource
    @Lazy
    VoucherOrderServiceImpl voucherOrderService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠卷不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return createVoucherWithRedissonLock(voucherId);
    }

    /**
     * 为当前用户创建订单，且保证一人一单，能够原子性地扣减库存，防止超卖
     * 使用本地锁
     * @param voucherId
     * @return
     */
    private Result creatVoucherWithLock(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 锁住用户 ID，防止多线程下同一用户重复购买
        synchronized (userId.toString().intern()) {
            return voucherOrderService.createVoucher(voucherId);
        }
    }

    /**
     * 为当前用户创建订单，且保证一人一单，能够原子性地扣减库存，防止超卖
     * 使用 redis 分布式锁
     * @param voucherId
     * @return
     */
    private Result creatVoucherWithDistributedLock(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock("voucher_order:" + userId, stringRedisTemplate);
        if (lock.tryLock(1200)) {
            try {
                return voucherOrderService.createVoucher(voucherId);
            } catch (Exception e) {
                // 处理异常
                return Result.fail("服务器异常，请稍后再试");
            } finally {
                lock.unlock();
            }
        } else {
            // 获取锁失败，可能是其他线程正在处理
            return Result.fail("请勿重复下单");
        }
    }

    private Result createVoucherWithRedissonLock(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 锁住用户 ID，防止多线程下同一用户重复购买
        String lockKey = "lock:voucher_order:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean success = lock.tryLock(1, 100, TimeUnit.SECONDS);
            if (!success) {
                return Result.fail("请勿重复下单");
            }
            return voucherOrderService.createVoucher(voucherId);
        } catch (Exception e) {
            // 处理异常
            return Result.fail("服务器异常，请稍后再试");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建订单(事务方法）
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 一人一单
        Long count = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId).count();
        if (count > 0) {
            // 用户已经购买过该优惠券
            return Result.fail("您已经购买过该优惠券！");
        }


        //5，扣减库存
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock -1")  // 能够原子更改数据，等同于 update seckill_voucher set stock = stock - 1
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock,0)      // 防止超卖
                .update();
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }


        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2.用户id
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }

}






























