package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 针对 Redis STRING 类型的存储策略实现。
 * <p>
 * 此策略将任何 Java 对象序列化为 JSON 字符串后，存储在 Redis 的 STRING 数据结构中。
 * <p>
 * An implementation of the storage strategy for the Redis STRING type.
 * This strategy serializes any Java object into a JSON string and stores it in a Redis STRING data structure.
 *
 * @param <T> 存储对象的类型。/ The type of the object to be stored.
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StringStorageStrategy<T> implements RedisStorageStrategy<T> {

    private final ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.STRING;
    }

    @Override
    public T read(RedisClient redisClient, String key, TypeReference<T> typeRef, ResolvedJMultiCacheConfig config) {
        Object rawValue = redisClient.get(key);
        if (rawValue == null) {
            return null;
        }

        if (JMultiCacheHelper.isSpecialEmptyData(rawValue, config)) {
            return (T) rawValue; // 是空值标记，直接返回 / It's a null value marker, return directly.
        }

        try {
            return objectMapper.convertValue(rawValue, typeRef);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to convert value for key: {}. Expected type: {}. Error: {}",
                    key, typeRef.getType().getTypeName(), e.getMessage());
            return null; // 转换失败，视为缓存未命中 / Conversion failed, treat as a cache miss.
        }
    }

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        Map<String, CompletableFuture<Optional<V>>> finalFutures = new HashMap<>();
        JavaType targetType = objectMapper.constructType(typeRef.getType());

        for (String key : keysToRead) {
            CompletableFuture<Object> rawFuture = batch.getAsync(key);

            CompletableFuture<Optional<V>> finalFuture = rawFuture.thenApply(rawValue -> {
                if (rawValue == null) {
                    return null; // Miss
                }
                if (JMultiCacheHelper.isSpecialEmptyData(rawValue, config)) {
                    return Optional.empty(); // Hit (empty)
                }
                try {
                    V convertedValue = objectMapper.convertValue(rawValue, targetType);
                    return Optional.of(convertedValue); // Hit (with data)
                } catch (Exception e) {
                    log.error("[JMultiCache-Strategy] Failed to convert value in multi-read for key: {}. Error: {}", key, e.getMessage());
                    return null; // Miss on error
                }
            });
            finalFutures.put(key, finalFuture);
        }
        return finalFutures;
    }

    @Override
    public void write(RedisClient redisClient, String key, T value, ResolvedJMultiCacheConfig config) {
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();
        redisClient.set(key, value, ttl);
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, T> dataToCache, ResolvedJMultiCacheConfig config) {
        if (dataToCache == null) return;
        final Duration ttl = config.getRedisTtl();
        dataToCache.forEach((key, value) -> {
            if (value != null) {
                batch.setAsync(key, value, ttl);
            }
        });
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        if (keysToMarkEmpty == null) return;
        final String placeholder = config.getEmptyValueMark();
        final Duration emptyTtl = config.getEmptyCacheTtl();
        keysToMarkEmpty.forEach(key -> batch.setAsync(key, placeholder, emptyTtl));
    }
}
