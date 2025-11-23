package io.github.vevoly.jmulticache.api;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * j-multi-cache 的管理接口，提供如缓存预热等管理类操作。
 * <p>
 * Management interface for j-multi-cache, providing administrative operations like preloading.
 *
 * @author vevoly
 */
public interface JMultiCacheAdmin {

    // =================================================================
    // ======================== 管理操作 / Management Operations ========
    // =================================================================

    /**
     * 手动预热缓存。
     * 将给定的 Map 数据批量写入到 L1 和 L2 缓存中。
     * <p>
     * Manually preloads the cache.
     * Batch writes the provided Map data into both L1 and L2 caches.
     *
     * @param multiCacheName 缓存配置名称。/ The cache configuration name.
     * @param dataToCache    待缓存的数据 Map (Key 为 Redis Key)。/ The data Map to cache (Key is the Redis Key).
     * @param <V>            数据的类型。/ The type of the data.
     * @return 成功写入的条目数。/ The number of successfully written entries.
     */
    <V> int preloadMultiCache(String multiCacheName, Map<String, V> dataToCache);

    /**
     * 清除一个缓存项。
     * <p>
     * Evicts a cache item.
     *
     * @param multiCacheName 缓存配置的唯一名称。/ The unique name of the cache configuration.
     * @param keyParams      用于构建要清除的缓存键的动态参数。/ The dynamic parameters to build the final cache key to evict.
     */
    void evict(String multiCacheName, Object... keyParams);
}
