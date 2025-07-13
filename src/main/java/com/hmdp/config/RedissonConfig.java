package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Redisson 配置类
 * <p>
 * 该配置类用于初始化 RedissonClient 实例，并将其注册为 Spring Bean。
 * 支持通过 application.yml 或 application.properties 文件配置 Redisson 的各种模式和参数。
 * <P></P>
 * 一般只有在小测试中会使用到 redisson-starter，在正式项目中为了和原有 redis 配置区分开，一般会使用原生的 redisson 依赖。
 * 常用方法：
 * tryLock() 方法，用于获取 Redisson 的分布式锁。可以指定等待超时时间和锁超时时间，默认30s释放锁。可以重入。
 * unlock() 方法，用于释放 Redisson 的分布式锁。不会出现一个线程意外释放了另一个线程持有的锁的问题。
 */
@Configuration
//@ConfigurationProperties(prefix = "spring.redisson") // 属性前缀
public class RedissonConfig {


    // Redisson 模式：single, cluster, sentinel, master_slave
    private String mode = "single"; // 默认为单机模式

    // Redis 服务器地址。单机模式格式：redis://127.0.0.1:6379；集群/哨兵/主从模式格式：redis://host1:port1,redis://host2:port2
    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private String port;

    // Redis 密码 (可选)
    @Value("${spring.data.redis.password}")
    private String password;

    // 数据库索引 (仅单机模式和主从模式的主节点有效)
    @Value("${spring.data.redis.database:0}")
    private int database;

    // Redis 服务器地址。单机模式格式：redis://127.0.0.1:6379；集群/哨兵/主从模式格式：redis://host1:port1,redis://host2:port2
    private String address;

    // 连接超时时间 (毫秒)
    private int timeout = 3000;

    // 连接池大小
    private int connectionPoolSize = 64;

    // 连接池最小空闲连接数
    private int connectionMinimumIdleSize = 10;

    // 从节点连接池大小 (集群/哨兵/主从模式)
    private int slaveConnectionPoolSize = 256;

    // 主节点连接池大小 (集群/哨兵/主从模式)
    private int masterConnectionPoolSize = 256;

    // 哨兵模式下的主服务器名 (仅哨兵模式有效)
    private String masterName;

    @Bean(destroyMethod = "shutdown") // 容器销毁时自动关闭 RedissonClient
    public RedissonClient redissonClient() {
        this.address = "redis://" + host + ":" + port;
        Config config = new Config();

        String[] redisAddresses = address.split(",");
        List<String> formattedAddresses = new ArrayList<>();
        for (String addr : redisAddresses) {
            if (!addr.startsWith("redis://")) {
                formattedAddresses.add("redis://" + addr.trim());
            } else {
                formattedAddresses.add(addr.trim());
            }
        }

        switch (mode.toLowerCase()) {
            case "single":
                SingleServerConfig singleServerConfig = config.useSingleServer()
                        .setAddress(formattedAddresses.get(0))
                        .setDatabase(database)
                        .setConnectionPoolSize(connectionPoolSize)
                        .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                        .setTimeout(timeout);
                if (StringUtils.hasText(password)) {
                    singleServerConfig.setPassword(password);
                }
                break;
            case "cluster":
                ClusterServersConfig clusterServersConfig = config.useClusterServers()
                        .addNodeAddress(formattedAddresses.toArray(new String[0]))
                        .setTimeout(timeout);
                if (StringUtils.hasText(password)) {
                    clusterServersConfig.setPassword(password);
                }
                break;
            case "sentinel":
                if (!StringUtils.hasText(masterName)) {
                    throw new IllegalArgumentException("Redisson 'masterName' cannot be empty in sentinel mode");
                }
                SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                        .setMasterName(masterName)
                        .addSentinelAddress(formattedAddresses.toArray(new String[0]))
                        .setDatabase(database) // 哨兵模式下连接主库的 database
                        .setTimeout(timeout)
                        .setMasterConnectionPoolSize(masterConnectionPoolSize)
                        .setSlaveConnectionPoolSize(slaveConnectionPoolSize);
                if (StringUtils.hasText(password)) {
                    sentinelServersConfig.setPassword(password);
                }
                break;
            case "master_slave":
            case "masterslave": // 兼容写法
                MasterSlaveServersConfig masterSlaveServersConfig = config.useMasterSlaveServers()
                        .setMasterAddress(formattedAddresses.get(0)) // 第一个通常是主地址
                        .setDatabase(database)
                        .setTimeout(timeout)
                        .setMasterConnectionPoolSize(masterConnectionPoolSize)
                        .setSlaveConnectionPoolSize(slaveConnectionPoolSize);

                if (formattedAddresses.size() > 1) {
                    List<String> slaveAddresses = formattedAddresses.subList(1, formattedAddresses.size());
                    masterSlaveServersConfig.addSlaveAddress(slaveAddresses.toArray(new String[0]));
                }
                if (StringUtils.hasText(password)) {
                    masterSlaveServersConfig.setPassword(password);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid Redisson mode: " + mode + ". Supported modes are: single, cluster, sentinel, master_slave.");
        }

        // 可以根据需要配置其他通用参数，例如 codec
        // config.setCodec(new org.redisson.codec.JsonJacksonCodec());

        return Redisson.create(config);
    }
}

