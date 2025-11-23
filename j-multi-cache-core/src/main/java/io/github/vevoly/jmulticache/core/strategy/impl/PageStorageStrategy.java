package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 针对分页对象 (例如 Spring Data 的 Page) 的存储策略实现。
 * <p>
 * 此策略将整个分页对象序列化为 JSON 字符串后，存储在 Redis 的 STRING 类型中。
 * 由于分页对象的复杂性和泛型不确定性，此策略不支持批量操作。
 * <p>
 * An implementation of the storage strategy for pagination objects (e.g., Spring Data's Page).
 * This strategy serializes the entire pagination object into a JSON string and stores it in a Redis STRING type.
 * Due to the complexity and generic uncertainty of pagination objects, this strategy does not support batch operations.
 *
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageStorageStrategy implements RedisStorageStrategy<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.PAGE;
    }

    @Override
    public Object read(RedisClient redisClient, String key, TypeReference<Object> typeRef, ResolvedJMultiCacheConfig config) {
        // 对于 Page 类型，我们期望值是 JSON 字符串 / For Page type, we expect the value to be a JSON string
        String json = redisClient.get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        if (JMultiCacheHelper.isSpecialEmptyData(json, config)) {
            return json;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to deserialize Page JSON for key: {}. JSON: '{}'. Error: {}", key, json, e.getMessage());
            return null;
        }
    }

    @Override
    public void write(RedisClient redisClient, String key, Object value, ResolvedJMultiCacheConfig config) {
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();
        try {
            // 如果 value 本身就是 String (空标记)，直接使用；否则序列化为 JSON / If the value itself is a String (the empty marker), use it directly; else serialize it to JSON.
            String jsonValue = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            redisClient.set(key, jsonValue, ttl);
        } catch (JsonProcessingException e) {
            log.error("[JMultiCache-Strategy] Failed to serialize Page value for key: {}. Error: {}", key, e.getMessage());
        }
    }

    // ==========================================================
    // == 不支持的批量操作 / Unsupported Batch Operations
    // ==========================================================

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("PageStorageStrategy does not support readMulti operation. Please use getSingleData for Page objects.");
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, Object> dataToCache, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("PageStorageStrategy does not support writeMulti operation.");
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("PageStorageStrategy does not support writeMultiEmpty operation.");
    }
}
