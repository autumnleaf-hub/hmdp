package com.hmdp.utils;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RIdGenerator;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Redisson的唯一ID生成器工具类
 * 支持多种ID生成策略：自增ID、雪花算法、带前缀ID等
 *
 * @author your-name
 * @since 1.0.0
 */
@Component
public class RedissonIdGenerator {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 缓存不同业务的ID生成器
     */
    private final ConcurrentHashMap<String, RIdGenerator> idGeneratorCache = new ConcurrentHashMap<>();

    /**
     * 缓存不同业务的原子长整型计数器
     */
    private final ConcurrentHashMap<String, RAtomicLong> atomicLongCache = new ConcurrentHashMap<>();

    /**
     * 工作机器ID（用于雪花算法）
     */
    private long workerId;

    /**
     * 数据中心ID（用于雪花算法）
     */
    private long datacenterId;

    @PostConstruct
    public void init() {
        // 初始化工作机器ID和数据中心ID
        try {
            InetAddress addr = InetAddress.getLocalHost();
            // 使用IP地址最后一段作为机器ID
            String hostAddress = addr.getHostAddress();
            this.workerId = Long.parseLong(hostAddress.substring(hostAddress.lastIndexOf(".") + 1));
            this.datacenterId = 1L; // 可以根据实际部署环境配置
        } catch (Exception e) {
            this.workerId = 1L;
            this.datacenterId = 1L;
        }
    }

    /**
     * 生成简单的自增ID
     *
     * @param businessType 业务类型，用于区分不同业务的ID序列
     * @return 自增ID
     */
    public long generateSimpleId(String businessType) {
        RAtomicLong atomicLong = getAtomicLong(businessType);
        return atomicLong.incrementAndGet();
    }

    /**
     * 生成带业务前缀的ID
     * 格式：{prefix}{yyyyMMdd}{6位自增序号}
     *
     * @param businessType 业务类型
     * @param prefix 业务前缀
     * @return 带前缀的ID字符串
     */
    public String generateBusinessId(String businessType, String prefix) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = businessType + ":" + dateStr;

        RAtomicLong atomicLong = getAtomicLong(key);
        long sequence = atomicLong.incrementAndGet();

        // 格式化为6位数字，不足补0
        return prefix + dateStr + String.format("%06d", sequence);
    }

    /**
     * 生成订单号
     * 格式：ORD{yyyyMMddHHmmss}{4位随机数}{4位自增序号}
     *
     * @return 订单号
     */
    public String generateOrderId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String key = "order:" + timestamp.substring(0, 8); // 按天分组

        RAtomicLong atomicLong = getAtomicLong(key);
        long sequence = atomicLong.incrementAndGet();

        // 生成4位随机数
        int random = (int) (Math.random() * 9000) + 1000;

        return "ORD" + timestamp + random + String.format("%04d", sequence % 10000);
    }

    /**
     * 使用Redisson的RIdGenerator生成ID
     * 这种方式性能更好，适合高并发场景
     *
     * @param businessType 业务类型
     * @return 生成的ID
     */
    public long generateHighPerformanceId(String businessType) {
        RIdGenerator idGenerator = getIdGenerator(businessType);
        return idGenerator.nextId();
    }

    /**
     * 生成分布式雪花算法ID
     * 使用Redis确保workerId的唯一性
     *
     * @param businessType 业务类型
     * @return 雪花算法生成的ID
     */
    public long generateSnowflakeId(String businessType) {
        // 使用Redis保证workerId在集群中的唯一性
        String workerKey = "snowflake:worker:" + businessType;
        RAtomicLong workerIdCounter = redissonClient.getAtomicLong(workerKey);
        long currentWorkerId = workerIdCounter.get();

        if (currentWorkerId == 0) {
            // 首次使用，设置过期时间为1小时，防止workerId泄露
            currentWorkerId = workerIdCounter.incrementAndGet();
            workerIdCounter.expire(java.time.Duration.ofHours(1));
        }

        return generateSnowflakeId(currentWorkerId % 32, datacenterId % 32);
    }

    /**
     * 批量生成ID，适合需要大量ID的场景
     *
     * @param businessType 业务类型
     * @param count 需要生成的ID数量
     * @return ID数组
     */
    public long[] generateBatchIds(String businessType, int count) {
        if (count <= 0 || count > 10000) {
            throw new IllegalArgumentException("批量生成ID数量必须在1-10000之间");
        }

        RAtomicLong atomicLong = getAtomicLong(businessType);
        long startId = atomicLong.addAndGet(count) - count + 1;

        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = startId + i;
        }
        return ids;
    }

    /**
     * 重置指定业务类型的ID序列
     * 注意：此操作会影响ID的唯一性，谨慎使用
     *
     * @param businessType 业务类型
     * @param newValue 新的起始值
     */
    public void resetIdSequence(String businessType, long newValue) {
        RAtomicLong atomicLong = getAtomicLong(businessType);
        atomicLong.set(newValue);
    }

    /**
     * 获取指定业务类型当前的ID值
     *
     * @param businessType 业务类型
     * @return 当前ID值
     */
    public long getCurrentId(String businessType) {
        RAtomicLong atomicLong = getAtomicLong(businessType);
        return atomicLong.get();
    }

    /**
     * 获取或创建RAtomicLong实例
     */
    private RAtomicLong getAtomicLong(String key) {
        return atomicLongCache.computeIfAbsent(key, k -> redissonClient.getAtomicLong("id:sequence:" + k));
    }

    /**
     * 获取或创建RIdGenerator实例
     */
    private RIdGenerator getIdGenerator(String businessType) {
        return idGeneratorCache.computeIfAbsent(businessType,
                k -> redissonClient.getIdGenerator("id:generator:" + k));
    }

    /**
     * 雪花算法实现
     */
    private long generateSnowflakeId(long workerId, long datacenterId) {
        // 雪花算法的基本实现
        long twepoch = 1288834974657L; // 起始时间戳 (2010-11-04)
        long workerIdBits = 5L;
        long datacenterIdBits = 5L;
        long maxWorkerId = -1L ^ (-1L << workerIdBits);
        long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
        long sequenceBits = 12L;
        long workerIdShift = sequenceBits;
        long datacenterIdShift = sequenceBits + workerIdBits;
        long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
        long sequenceMask = -1L ^ (-1L << sequenceBits);

        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }

        long timestamp = System.currentTimeMillis();
        long sequence = 0L;

        // 使用Redis保证序列号的原子性
        String seqKey = "snowflake:seq:" + workerId + ":" + datacenterId + ":" + timestamp;
        RAtomicLong seqAtomic = redissonClient.getAtomicLong(seqKey);
        sequence = seqAtomic.incrementAndGet() & sequenceMask;

        // 设置过期时间，防止内存泄露
        seqAtomic.expire(java.time.Duration.ofSeconds(1));

        if (sequence == 0) {
            timestamp = tilNextMillis(timestamp);
        }

        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    /**
     * 等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 清理缓存，释放资源
     */
    public void clearCache() {
        idGeneratorCache.clear();
        atomicLongCache.clear();
    }
}

