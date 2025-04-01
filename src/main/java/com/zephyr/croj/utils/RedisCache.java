package com.zephyr.croj.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存工具类
 */
@Component
public class RedisCache {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 存入缓存
     *
     * @param key 键
     * @param value 值
     */
    public <T> void setCacheObject(final String key, final T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 存入缓存并设置时间
     *
     * @param key 键
     * @param value 值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 获取缓存
     *
     * @param key 键
     * @return 值
     */
    public <T> T getCacheObject(final String key) {
        ValueOperations<String, Object> operation = redisTemplate.opsForValue();
        return (T) operation.get(key);
    }

    /**
     * 删除缓存
     *
     * @param key 键
     * @return 是否成功
     */
    public boolean deleteObject(final String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }
}