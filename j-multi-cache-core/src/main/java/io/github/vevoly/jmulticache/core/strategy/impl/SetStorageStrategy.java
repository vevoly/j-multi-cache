package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import io.github.vevoly.jmulticache.api.wrap.UnionReadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 针对 Redis SET 类型的存储策略实现。
 * <p>
 * 此策略将 Java 的 Set 结构映射到 Redis 的 Set 数据结构，并特别实现了 {@code readUnion} 方法以支持 SUNION 操作。
 * <p>
 * An implementation of the storage strategy for the Redis SET type.
 * This strategy maps a Java Set structure to a Redis Set data structure and specifically implements the {@code readUnion} method to support SUNION operations.
 *
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetStorageStrategy implements RedisStorageStrategy<Set<?>> {

    private final ObjectMapper objectMapper;

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.SET;
    }

    @Override
    public Set<?> read(RedisClient redisClient, String key, TypeReference<Set<?>> typeRef, ResolvedJMultiCacheConfig config) {
        Set<String> rawSet = redisClient.getSet(key);
        if (CollectionUtils.isEmpty(rawSet)) {
            return null;
        }
        // 获取已过滤掉空值标记的Set / Get the Set filtered out the empty value placeholder
        Set<String> cleanSet = getCleanSet(rawSet, config);
        try {
            return objectMapper.convertValue(cleanSet, typeRef);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to convert clean Set value in multi-read for key: {}. Error: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        Map<String, CompletableFuture<Optional<V>>> finalFutures = new HashMap<>();

        for (String key : keysToRead) {
            // 获取原始Set的Future / obtain raw Future of Set
            CompletableFuture<Set<Object>> rawFuture = batch.setGetAllAsync(key);

            finalFutures.put(key, rawFuture.thenApply(rawSet -> {
                if (rawSet == null || rawSet.isEmpty()) {
                    return null; // Miss
                }
                if (CollectionUtils.isEmpty(rawSet)) {
                    return Optional.empty(); // Hit (empty)
                }
                try {
                    V value = objectMapper.convertValue(rawSet, typeRef);
                    return Optional.of(value);
                } catch (Exception e) {
                    log.error("[JMultiCache-Strategy] Failed to convert clean Set value in multi-read for key: {}. Error: {}", key, e.getMessage());
                    return null;
                }
            }));
        }
        return finalFutures;
    }

    @Override
    public void write(RedisClient redisClient, String key, Set<?> value, ResolvedJMultiCacheConfig config) {
        boolean isEmptyMark = JMultiCacheHelper.isSpecialEmptyData(value, config);
        Duration ttl = isEmptyMark ? config.getEmptyCacheTtl() : config.getRedisTtl();

        if (value == null || value.isEmpty()) {
            redisClient.delete(key);
            return;
        }

        // 将成员转换为 String 数组 / convert members to String array
        String[] stringMembers = value.stream()
                .map(String::valueOf)
                .toArray(String[]::new);

        redisClient.sAdd(key, ttl, stringMembers);
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, Set<?>> dataToCache, ResolvedJMultiCacheConfig config) {
        if (dataToCache == null) return;
        final Duration ttl = config.getRedisTtl();

        dataToCache.forEach((key, valueSet) -> {
            if (valueSet != null && !valueSet.isEmpty()) {
                batch.setDeleteAsync(key);
                // 使用 ObjectMapper 将对象序列化为 JSON 字符串
                Set<String> jsonMembers = valueSet.stream()
                        .map(member -> {
                            try {
                                return objectMapper.writeValueAsString(member);
                            } catch (Exception e) {
                                log.error("[JMultiCache-Strategy] Failed to serialize member to JSON: {}", member, e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                if (!jsonMembers.isEmpty()) {
                    batch.setAddAllAsync(key, jsonMembers);
                    batch.expireAsync(key, ttl);
                }
            }
        });
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        if (keysToMarkEmpty == null || keysToMarkEmpty.isEmpty()) {
            return;
        }
        final String placeholder = config.getEmptyValueMark();
        final Duration emptyTtl = config.getEmptyCacheTtl();
        for (String key : keysToMarkEmpty) {
            batch.setDeleteAsync(key);
            batch.setAddAsync(key, placeholder);
            batch.expireAsync(key, emptyTtl);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> UnionReadResult<E> readUnion(RedisClient redisClient, List<String> keys, TypeReference<Set<E>> typeRef, ResolvedJMultiCacheConfig config) {
        if (CollectionUtils.isEmpty(keys)) {
            return UnionReadResult.empty();
        }
        try {
            Map<String, Object> rawResult = redisClient.sunionAndFindMisses(keys);
            Set<String> rawUnionSet = (Set<String>) rawResult.get(JMultiCacheConstants.UNION_RESULT);
            List<String> missedKeys = (List<String>) rawResult.get(JMultiCacheConstants.UNION_MISSED_KEYS);

            if (CollectionUtils.isEmpty(rawUnionSet)) {
                return new UnionReadResult<>(Collections.emptySet(), missedKeys);
            }

            Set<E> unionResult = convertRawSetToTypedSet(rawUnionSet, typeRef, config);
            return new UnionReadResult<>(unionResult, missedKeys);
        } catch (Exception e) {
            log.error("[JMultiCache-Strategy] Failed to read union for keys: {}. Error: {}", keys, e.getMessage(), e);
            return new UnionReadResult<>(Collections.emptySet(), keys);
        }
    }

    /**
     * 接收一个从 Redis 获取的原始 Set，并返回一个清除了所有空值标记成员的“干净”Set。
     *
     * @param rawSet   从 Redis 获取的原始 Set<Object>。
     * @param config   当前操作的配置，用于获取空值标记。如果为 null，则使用默认空值标记。
     * @return 一个不包含任何空值标记成员的新 Set。
     */
    private Set<String> getCleanSet(Set<String> rawSet, ResolvedJMultiCacheConfig config) {
        if (CollectionUtils.isEmpty(rawSet)) {
            return Collections.emptySet();
        }

        return rawSet.stream()
                .filter(member -> !JMultiCacheHelper.isSpecialEmptyData(member, config))
                .collect(Collectors.toSet());
    }

    /**
     * 将一个从 Redis 获取的原始 Set<Object>，安全地转换为一个指定泛型类型的 Set。
     * <p>
     * 此方法会先过滤掉所有已知的空值标记，然后再进行类型转换。
     * <p>
     * Safely converts a raw Set<Object> from Redis to a typed Set of a specified generic type.
     * This method filters out all known null value markers before performing the type conversion.
     *
     * @param rawSet   从 Redis 获取的原始 Set<Object>。/ The raw Set<Object> obtained from Redis.
     * @param typeRef  目标类型的 TypeReference。/ The TypeReference of the target type.
     * @param config   当前操作的配置。/ The configuration for the current operation.
     * @return 转换后的、类型安全的 Set<?>。/ The converted, type-safe Set<?>.
     */
    private <E>Set<E> convertRawSetToTypedSet(Set<String> rawSet, TypeReference<Set<E>> typeRef, ResolvedJMultiCacheConfig config) {
        // 1. 清理数据（处理空值占位符）
        Set<String> cleanSet = this.getCleanSet(rawSet, config);
        if (CollectionUtils.isEmpty(cleanSet)) {
            return Collections.emptySet();
        }
        // 2. 核心修复：从 TypeReference 中提取泛型 E 的具体类型
        JavaType collectionType = objectMapper.getTypeFactory().constructType(typeRef.getType());
        // 3. 准备结果集合
        Set<E> result = new HashSet<>(cleanSet.size());
        // 4. 逐个反序列化 (Parse each JSON string)
        for (String jsonString : cleanSet) {
            try {
                E element = objectMapper.readValue(jsonString, collectionType);
                result.add(element);
            } catch (Exception e) {
                log.error("[JMultiCache-Strategy] Failed to deserialize element in Set. JSON: {}", jsonString, e);
            }
        }
        return result;
    }
}
