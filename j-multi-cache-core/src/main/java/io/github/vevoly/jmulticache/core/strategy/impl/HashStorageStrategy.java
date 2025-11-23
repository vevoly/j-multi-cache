package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.FieldBasedStorageStrategy;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 针对 Redis HASH 类型的存储策略实现。
 * <p>
 * 此策略支持整体存取 (Map) 和字段级存取 (field-based)。
 * <p>
 * An implementation of the storage strategy for the Redis HASH type.
 * This strategy supports both whole-map access and field-based access.
 *
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HashStorageStrategy implements RedisStorageStrategy<Map<String, ?>>, FieldBasedStorageStrategy<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.HASH;
    }

    // ==========================================================
    // == 整体存取 / Whole-Map Access
    // ==========================================================

    @Override
    public Map<String, ?> read(RedisClient redisClient, String key, TypeReference<Map<String, ?>> typeRef, ResolvedJMultiCacheConfig config) {
        Map<String, Object> map = redisClient.hgetAll(key);
        if (MapUtils.isEmpty(map)) {
            return null;
        }

        if (JMultiCacheHelper.isSpecialEmptyData(map, config)) {
            return map;
        }

        // HASH 类型的 value 通常是 String，需要根据 typeRef 进行二次转换 / the type of HASH value is usually String, so we need to convert it again based on typeRef
        try {
            return objectMapper.convertValue(map, typeRef);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to convert Hash value for key: {}. Error: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void write(RedisClient redisClient, String key, Map<String, ?> value, ResolvedJMultiCacheConfig config) {
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();
        redisClient.hmset(key, (Map<String, Object>) value, ttl);
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        if (keysToMarkEmpty == null) return;
        final String placeholder = config.getEmptyValueMark();
        final Duration emptyTtl = config.getEmptyCacheTtl();
        final Map<String, Object> emptyMap = Collections.singletonMap(placeholder, Boolean.TRUE);

        keysToMarkEmpty.forEach(key -> {
            batch.hashPutAllAsync(key, emptyMap);
            batch.expireAsync(key, emptyTtl);
        });
    }

    // ==========================================================
    // == 字段级存取 / Field-Based Access
    // ==========================================================

    @Override
    public Object readField(RedisClient redisClient, String key, String field, Class<Object> fieldType, ResolvedJMultiCacheConfig config) {
        Object rawValue = redisClient.hget(key, field);
        if (rawValue == null) {
            return null;
        }

        if (JMultiCacheHelper.isSpecialEmptyData(rawValue, config)) {
            return rawValue;
        }

        try {
            return objectMapper.convertValue(rawValue, fieldType);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to convert Hash field value for key: {}, field: {}. Error: {}", key, field, e.getMessage());
            return null;
        }
    }

    @Override
    public void writeField(RedisClient redisClient, String key, String field, Object value, ResolvedJMultiCacheConfig config) {
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();
        redisClient.hset(key, field, value, ttl);
    }

    // ==========================================================
    // == 暂不支持的批量操作 / Unsupported Batch Operations
    // ==========================================================

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        log.warn("HashStorageStrategy.readMulti is not fully implemented yet. Returning empty map.");
        return Collections.emptyMap();
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, Map<String, ?>> dataToCache, ResolvedJMultiCacheConfig config) {
        log.warn("HashStorageStrategy.writeMulti is not fully implemented yet.");
    }
}
