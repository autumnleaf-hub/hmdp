package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    TransactionTemplate transactionTemplate;

    @Resource
    @Lazy
    VoucherOrderServiceImpl voucherOrderServiceImpl;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    // 秒杀订单 Lua 脚本
    // 脚本检查了库存、一人一单，如有资格则还会在 redis 中对相应的用户和订单进行标记，
    private static final DefaultRedisScript<Long> SECKILL_VOUCHER_ORDER_SCRIPT;
    static {
        SECKILL_VOUCHER_ORDER_SCRIPT = new DefaultRedisScript<>();
        SECKILL_VOUCHER_ORDER_SCRIPT.setLocation(new ClassPathResource("lua/validateVoucherOrder.lua"));
        SECKILL_VOUCHER_ORDER_SCRIPT.setResultType(Long.class);
    }

    // 确认能够下单后，会将订单信息放入阻塞队列中，后续会有单独的线程来处理这些订单
    private ArrayBlockingQueue<VoucherOrder> voucherOrderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 处理下单任务的线程池
    private static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init() {
        // 启动处理订单的线程
        SECKILL_ORDER_EXECUTOR.submit(new SeckillOrderHandler());
    }

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
     * 秒杀优化，配合 redis 通过异步的方式实现下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherAsync(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_VOUCHER_ORDER_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // TODO 将订单任务提交到人物队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        try {
            voucherOrderTasks.put(voucherOrder);
        } catch (InterruptedException e) {
            log.error("放入订单任务队列失败: {}", e.getMessage());
            return Result.fail("服务器异常，请稍后再试");
        }

        //3.返回订单id
        return Result.ok(orderId);
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
            return voucherOrderServiceImpl.createVoucherOrder(voucherId);
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
                return voucherOrderServiceImpl.createVoucherOrder(voucherId);
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
            return voucherOrderServiceImpl.createVoucherOrder(voucherId);
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
    public Result createVoucherOrder(Long voucherId) {
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

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long count = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("用户尝试 {} 重复下单，优惠券信息: {}", userId, voucherOrder);
            return;
        }

        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock -1")  // 能够原子更改数据，等同于 update seckill_voucher set stock = stock - 1
                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)      // 防止超卖
                .update();
        if (!success) {
            log.error("库存不足，无法处理订单: {}", voucherOrder);
            return;
        }

        // 创建订单
        save(voucherOrder);

        log.info("成功处理订单: {}", voucherOrder);
    }

    private class SeckillOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 从阻塞队列中获取订单
                    VoucherOrder voucherOrder = voucherOrderTasks.take();
                    // 处理订单
                    processVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    break; // 退出循环
                } catch (Exception e) {
                    e.printStackTrace(); // 处理异常
                }
            }
        }

        private void processVoucherOrder(VoucherOrder voucherOrder) {
            // 获取用户ID
            Long userId = voucherOrder.getUserId();
            // 获取锁对象
            RLock lock = redissonClient.getLock("lock:" + RedisConstants.SECKILL_ORDER_KEY + userId);
            // 尝试获取锁
            boolean isLock = lock.tryLock();
            // 判断锁是否获取成功
            if (!isLock) {
                log.error("用户尝试重复下单");
                return;
            }
            try {
                voucherOrderServiceImpl.createVoucherOrder(voucherOrder);
            } finally {
                lock.unlock();
            }
        }
    }

}






























