package com.hmdp.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 封装 StringRedisTemplate，提供更便捷的 Redis 操作方法，包括对象的 JSON 序列化存储。
 */
@Component
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper; // 用于 JSON 序列化和反序列化

    @Autowired
    public RedisUtil(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // ------------------- Key 相关操作 -------------------

    /**
     * 删除单个 key
     *
     * @param key 键
     * @return 是否成功删除
     */
    public boolean delete(String key) {
        if (key == null) {
            return false;
        }
        return stringRedisTemplate.delete(key);
    }

    /**
     * 批量删除 key
     *
     * @param keys 键集合
     * @return 成功删除的个数
     */
    public Long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return stringRedisTemplate.delete(keys);
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true 存在 false 不存在
     */
    public boolean hasKey(String key) {
        if (key == null) {
            return false;
        }
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 设置 key 的过期时间
     *
     * @param key     键
     * @param timeout 时间(秒)
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置 key 的过期时间
     *
     * @param key     键
     * @param timeout 时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        if (key == null || timeout <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取 key 的过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效，负数代表已过期或不存在
     */
    public Long getExpire(String key) {
        if (key == null) {
            return null; // 或者抛出异常，取决于您的错误处理策略
        }
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 查找匹配的key
     * @param pattern 表达式 (例如: "user:*")
     * @return 匹配的key集合
     */
    public Set<String> keys(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        return stringRedisTemplate.keys(pattern);
    }


    // ------------------- String 类型操作 -------------------

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        if (key == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 时间(秒) timeout要大于0 如果timeout小于等于0 将设置为无限期
     */
    public void set(String key, String value, long timeout) {
        set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 时间
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        if (key == null) {
            return;
        }
        if (timeout > 0) {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } else {
            set(key, value);
        }
    }

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        if (key == null) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return 增加后的值
     */
    public Long increment(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("递增因子必须大于0");
        }
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几
     * @return 减少后的值
     */
    public Long decrement(String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("递减因子必须大于0"); // 注意：这里delta应该是正数，表示减少的量
        }
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }

    // ------------------- Object 类型操作 (JSON序列化) -------------------

    /**
     * 存储对象 (JSON序列化)
     *
     * @param key    键
     * @param value  对象
     */
    public <T> void setObject(String key, T value) {
        if (key == null || value == null) {
            return;
        }
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, jsonValue);
        } catch (JsonProcessingException e) {
            // 实际项目中应该记录日志或抛出自定义异常
            throw new RuntimeException("Redis setObject 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 存储对象并设置过期时间 (JSON序列化)
     *
     * @param key     键
     * @param value   对象
     * @param timeout 时间(秒)
     */
    public <T> void setObject(String key, T value, long timeout) {
        setObject(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 存储对象并设置过期时间 (JSON序列化)
     *
     * @param key     键
     * @param value   对象
     * @param timeout 时间
     * @param unit    时间单位
     */
    public <T> void setObject(String key, T value, long timeout, TimeUnit unit) {
        if (key == null || value == null) {
            return;
        }
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            if (timeout > 0) {
                stringRedisTemplate.opsForValue().set(key, jsonValue, timeout, unit);
            } else {
                stringRedisTemplate.opsForValue().set(key, jsonValue);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis setObjectWithExpire 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取对象 (JSON反序列化)
     *
     * @param key   键
     * @param clazz 对象的Class类型
     * @return 对象实例，如果key不存在或反序列化失败则返回null
     */
    public <T> T getObject(String key, Class<T> clazz) {
        if (key == null || clazz == null) {
            return null;
        }
        String jsonValue = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(jsonValue)) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonValue, clazz);
        } catch (JsonProcessingException e) {
            // 实际项目中应该记录日志或抛出自定义异常
            // 可以考虑返回null或抛出异常，取决于业务需求
            System.err.println("Redis getObject 反序列化失败: " + e.getMessage() + " for key: " + key + " and value: " + jsonValue);
            return null;
        }
    }

    /**
     * 获取对象列表 (JSON反序列化)
     *
     * @param key   键
     * @param elementClazz 列表中元素的Class类型
     * @return 对象列表实例，如果key不存在或反序列化失败则返回null或空List
     */
    public <T> List<T> getList(String key, Class<T> elementClazz) {
        if (key == null || elementClazz == null) {
            return null;
        }
        String jsonValue = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(jsonValue)) {
            return null; // 或者 Collections.emptyList();
        }
        try {
            // objectMapper.getTypeFactory().constructCollectionType(List.class, elementClazz) 用于构建 List<T> 的类型
            return objectMapper.readValue(jsonValue, objectMapper.getTypeFactory().constructCollectionType(List.class, elementClazz));
        } catch (JsonProcessingException e) {
            System.err.println("Redis getList 反序列化失败: " + e.getMessage() + " for key: " + key + " and value: " + jsonValue);
            return null; // 或者 Collections.emptyList();
        }
    }
}
