package com.hmdp.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RedisUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper; // 用于 JSON 序列化和反序列化

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
            log.error("Redis getObject 反序列化失败: {} for key: {} and value: {}", e.getMessage(), key, jsonValue);
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
            log.error("Redis getList 反序列化失败: {} for key: {} and value: {}", e.getMessage(), key, jsonValue);
            return null; // 或者 Collections.emptyList();
        }
    }

    // ------------------- 基础操作方法 -------------------

    /**
     * 判断key是否存在
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 删除key
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 批量删除key
     */
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    /**
     * 获取所有匹配的key
     */
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    // ------------------- String 类型操作 -------------------

    /**
     * 普通缓存获取
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 普通缓存放入
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 普通缓存放入并设置时间
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 递增
     */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 递增指定值
     */
    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减
     */
    public Long decrement(String key) {
        return stringRedisTemplate.opsForValue().decrement(key);
    }

    /**
     * 递减指定值
     */
    public Long decrement(String key, long delta) {
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }
}
