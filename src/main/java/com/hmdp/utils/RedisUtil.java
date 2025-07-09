package com.hmdp.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.ReadOnlyProperty;
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
public class RedisUtil extends StringRedisTemplate{

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
            super.opsForValue().set(key, jsonValue);
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
                super.opsForValue().set(key, jsonValue, timeout, unit);
            } else {
                super.opsForValue().set(key, jsonValue);
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
        String jsonValue = super.opsForValue().get(key);
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
        String jsonValue = super.opsForValue().get(key);
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
}
