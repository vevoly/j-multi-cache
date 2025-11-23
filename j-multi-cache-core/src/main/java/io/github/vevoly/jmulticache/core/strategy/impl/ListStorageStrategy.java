package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 针对 Redis LIST 类型的存储策略实现。
 * <p>
 * 此策略将 Java 的 List 结构映射到 Redis 的 List 数据结构。
 * <p>
 * An implementation of the storage strategy for the Redis LIST type.
 * This strategy maps a Java List structure to a Redis List data structure.
 *
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListStorageStrategy implements RedisStorageStrategy<List<?>> {

    private final ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.LIST;
    }

    @Override
    public List<?> read(RedisClient redisClient, String key, TypeReference<List<?>> typeRef, ResolvedJMultiCacheConfig config) {
        List<Object> rawList = redisClient.getList(key);
        if (CollectionUtils.isEmpty(rawList)) {
            return null;
        }
        // 使用 config (如果可用) 或默认值来判断空标记
        if (JMultiCacheHelper.isSpecialEmptyData(rawList, config)) {
            return rawList;
        }

        try {
            return objectMapper.convertValue(rawList, typeRef);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to convert List value for key: {}. Error: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        Map<String, CompletableFuture<Optional<V>>> finalFutures = new HashMap<>();
        JavaType targetType = objectMapper.constructType(typeRef.getType());

        for (String key : keysToRead) {
            CompletableFuture<List<Object>> rawFuture = batch.listGetAllAsync(key);
            CompletableFuture<Optional<V>> finalFuture = rawFuture.thenApply(rawList -> {
                if (rawList == null || rawList.isEmpty()) {
                    return null; // Redis中没有这个Key / No this key in Redis
                }
                if (JMultiCacheHelper.isSpecialEmptyData(rawList, config)) {
                    return Optional.empty(); // 命中空值占位符 / Hit empty mark
                }
                try {
                    // 将 Redis 返回的 List<Object> (实际是List<String>) 反序列化为调用者期望的类型 V / Deserialize the List<Object> (actually List<String>) returned by Redis into the type V expected by the caller
                    // 如果 V 是 List<MyEntity>，这里就会得到一个 ArrayList<MyEntity> / if V is List<MyEntity>, we get an ArrayList<MyEntity> here
                    V value = objectMapper.convertValue(rawList, targetType);
                    return Optional.of(value); // Hit (with data)
                } catch (Exception e) {
                    log.error("[JMultiCache-Strategy] Failed to convert List value in multi-read for key: {}. Error: {}", key, e.getMessage());
                    return null; // Miss on error
                }
            });
            finalFutures.put(key, finalFuture);
        }
        return finalFutures;
    }

    @Override
    public void write(RedisClient redisClient, String key, List<?> value, ResolvedJMultiCacheConfig config) {
        // 根据写入的是真实数据还是空标记，从 config 中选择正确的 TTL / Cording to whether the write is real data or an empty mark, select the correct TTL from config
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();
        redisClient.setList(key, value, ttl);
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, List<?>> dataToCache, ResolvedJMultiCacheConfig config) {
        if (dataToCache == null) return;
        final Duration ttl = config.getRedisTtl();
        dataToCache.forEach((key, valueList) -> {
            if (valueList != null && !valueList.isEmpty()) {
                batch.listDeleteAsync(key);
                batch.listAddAllAsync(key, valueList);
                batch.expireAsync(key, ttl);
            }
        });
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        if (keysToMarkEmpty == null) return;
        final String placeholder = config.getEmptyValueMark();
        final Duration emptyTtl = config.getEmptyCacheTtl();
        final List<String> emptyList = Collections.singletonList(placeholder);

        keysToMarkEmpty.forEach(key -> {
            batch.listDeleteAsync(key);
            batch.listAddAllAsync(key, emptyList);
            batch.expireAsync(key, emptyTtl);
        });
    }
}
