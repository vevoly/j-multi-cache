package io.github.vevoly.jmulticache.core.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.structure.JMultiCacheScorable;
import io.github.vevoly.jmulticache.api.structure.JMultiCacheScoredEntry;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheInternalHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Redis ZSet (Sorted Set) 存储策略实现。
 * <p>
 * 适用于排行榜、延迟队列、带权重的列表等场景。
 * <p>
 * <h3>用户注意事项 / User Instructions:</h3>
 * <ol>
 *     <li>
 *         <strong>必须实现接口 / Must implement interface:</strong><br>
 *         配置文件中 {@code entity-class} 指定的类必须实现 {@link JMultiCacheScorable} 接口。<br>
 *         The class specified in {@code entity-class} must implement the {@link JMultiCacheScorable} interface.
 *     </li>
 *     <li>
 *         <strong>数据一致性 / Data Consistency:</strong><br>
 *         建议仅在 ZSet 中存储 ID 和 Score（轻量级 DTO），详细业务数据通过 ID 二次查询。<br>
 *         It is recommended to store only ID and Score (Lightweight DTO) in ZSet, and query detailed business data via ID.
 *     </li>
 *     <li>
 *         <strong>空值处理 / Empty Value Handling:</strong><br>
 *         为了防止缓存穿透，空列表会以 String 类型存储占位符。策略类会自动处理类型冲突。<br>
 *         To prevent cache penetration, empty lists are stored as String placeholders. This strategy automatically handles type conflicts.
 *     </li>
 * </ol>
 *
 * @author vevoly
 * @see JMultiCacheScorable
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZSetStorageStrategy implements RedisStorageStrategy<List<?>> {

    @Override
    public String getStorageType() {
        return DefaultStorageTypes.ZSET;
    }

    /**
     * 读取 ZSet 数据。
     * <p>
     * Reads ZSet data.
     *
     * @param redisClient Redis 客户端 / Redis client
     * @param key         缓存 Key / Cache Key
     * @param typeRef     类型引用 / Type reference
     * @param config      配置对象 / Configuration object
     * @return 解析后的对象列表，如果未命中则返回 null / Parsed object list, or null if miss.
     */
    @Override
    public List<?> read(RedisClient redisClient, String key, TypeReference<List<?>> typeRef, ResolvedJMultiCacheConfig config) {
        // 1. 类型检查 (防止空值占位符导致的 WRONGTYPE 异常) / Check type (Prevent WRONGTYPE exception caused by empty placeholders)
        String type = redisClient.type(key);
        if (DefaultStorageTypes.STRING.equalsIgnoreCase(type)) {
            // 是 String，说明是空值占位符，返回空列表 (Hit Empty) / If it's String, meaning it's an empty placeholder, return empty list.
            return Collections.emptyList();
        }
        if (JMultiCacheConstants.NONE.equalsIgnoreCase(type)) {
            // 不存在，返回 null (Cache Miss) / Does not exist, return null.
            return null;
        }
        if (!DefaultStorageTypes.ZSET.equalsIgnoreCase(type)) {
            log.warn("[JMultiCache] Key '{}' exists but type is '{}' (expected zset or string). Treating as miss.", key, type);
            return null;
        }

        // 2. 验证 Entity Class 是否遵守约定 / Verify if Entity Class follows the convention
        Class<?> entityClass = config.getEntityClass();
        if (!JMultiCacheScorable.class.isAssignableFrom(entityClass)) {
            throw new IllegalStateException("[JMultiCache] ZSet storage requires entity class [" + entityClass.getName() + "] to implement JMultiCacheScorable interface.");
        }

        // 3. 读取 Redis (带分数读取) / Read Redis (Read with scores)
        Collection<JMultiCacheScoredEntry<String>> entries = redisClient.zRangeWithScores(key, 0, -1);
        if (CollectionUtils.isEmpty(entries)) {
            return null;
        }

        // 4. 转换回 DTO 列表 / Convert back to DTO list
        List<Object> resultList = new ArrayList<>(entries.size());
        try {
            for (JMultiCacheScoredEntry<String> entry : entries) {
                // 实例化对象 / Instantiate object
                JMultiCacheScorable item = (JMultiCacheScorable) entityClass.getDeclaredConstructor().newInstance();
                // 回填 ID 和 Score / Fill in ID and Score
                item.setCacheId(entry.getValue());
                item.setCacheScore(entry.getScore());
                resultList.add(item);
            }
        } catch (Exception e) {
            log.error("[JMultiCache] Failed to deserialize ZSet member to '{}'.", entityClass.getName(), e);
            return null;
        }
        return resultList;
    }

    /**
     * 批量读取 (暂未实现完全的 Pipeline ZSet 读取，这里作为预留接口)。
     * <p>
     * Batch read (Full Pipeline ZSet read is not yet implemented, reserved interface).
     */
    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("ZSet storage does not support batch read yet.");
    }

    /**
     * 写入 ZSet 数据。
     * <p>
     * Write ZSet data.
     *
     * @param redisClient Redis 客户端 / Redis client
     * @param key         缓存 Key / Cache Key
     * @param value       要写入的列表数据 / List data to write
     * @param config      配置对象 / Configuration object
     */
    @Override
    public void write(RedisClient redisClient, String key, List<?> value, ResolvedJMultiCacheConfig config) {
        // 1. 处理空值缓存 (防穿透) / Handle empty cache (Anti-penetration)
        if (JMultiCacheHelper.isSpecialEmptyData(value, config)) {
            redisClient.set(key, value, config.getEmptyCacheTtl());
            return;
        }

        if (CollectionUtils.isEmpty(value)) {
            return; // 空列表不写入 ZSet / Empty list is not written
        }
        // 2. 准备数据 / Prepare data
        Map<Object, Double> zsetMap = new HashMap<>();
        for (Object item : value) {
            if (!(item instanceof JMultiCacheScorable)) {
                throw new IllegalArgumentException("[JMultiCache] Object must implement JMultiCacheScorable: " + item.getClass().getName());
            }
            JMultiCacheScorable scorable = (JMultiCacheScorable) item;
            zsetMap.put(scorable.getCacheId(), scorable.getCacheScore());
        }

        // 3. 写入 Redis / Write to Redis
        if (!zsetMap.isEmpty()) {
            redisClient.zAdd(key, zsetMap, config.getRedisTtl());
        }
    }

    /**
     * 批量写入。
     * <p>
     * Batch write.
     */
    @Override
    public void writeMulti(BatchOperation batch, Map<String, List<?>> dataToCache, ResolvedJMultiCacheConfig config) {
        if (CollectionUtils.isEmpty(dataToCache)) return;
        final Duration ttl = config.getRedisTtl();

        dataToCache.forEach((key, list) -> {
            // 1. 处理空值占位符 / Handle empty cache (Anti-penetration)
            if (JMultiCacheHelper.isSpecialEmptyData(list, config)) {
                batch.setAsync(key, config.getEmptyValueMark(), config.getEmptyCacheTtl());
                return;
            }
            // 2. 处理正常数据 / Handle normal data
            if (!CollectionUtils.isEmpty(list)) {
                Map<Object, Double> zsetMap = new HashMap<>();
                for (Object item : list) {
                    if (item instanceof JMultiCacheScorable) {
                        JMultiCacheScorable scorable = (JMultiCacheScorable) item;
                        zsetMap.put(scorable.getCacheId(), scorable.getCacheScore());
                    } else {
                        log.warn("[JMultiCache] Skipping item in batch write, not instance of JMultiCacheScorable: {}", item);
                    }
                }

                if (!zsetMap.isEmpty()) {
                    batch.setDeleteAsync(key);
                    batch.zAddAsync(key, zsetMap);
                    batch.expireAsync(key, ttl);
                }
            }
        });
    }

    /**
     * 批量写入空值占位符。
     * <p>
     * Batch write empty value placeholders.
     */
    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        if (CollectionUtils.isEmpty(keysToMarkEmpty)) return;
        // 使用 String 结构存储空值标记 / Store empty markers using String structure
        Object emptyVal = JMultiCacheInternalHelper.createEmptyData(null, config); // 获取配置中的空值标记
        Duration ttl = config.getEmptyCacheTtl();
        for (String key : keysToMarkEmpty) {
            batch.setAsync(key, emptyVal, ttl);
        }
    }
}
