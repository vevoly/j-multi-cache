package io.github.vevoly.jmulticache.api;

import java.util.Map;
import java.util.Set;

/**
 * j-multi-cache 的管理接口，提供如缓存预热等管理类操作。
 * <p>
 * Management interface for j-multi-cache, providing administrative operations like preloading.
 *
 * @author vevoly
 */
public interface JMultiCacheOps {

    /**
     * 根据配置和参数，计算最终的 Redis Key。
     * 会执行 SpEL 表达式解析。
     * <p>
     * According to the configuration and parameters, calculate the final Redis Key.
     * SpEL expression parsing is performed.
     *
     * @param multiCacheName 缓存配置名 / The cache configuration name.
     * @param keyParams      参数列表  / The parameter list.
     * @return 完整的 Redis Key
     */
    String computeKey(String multiCacheName, Object... keyParams);

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
     * [集群广播] 清除一个缓存项。
     * 1. 删除 L2 Redis。
     * 2. 删除本机 L1。
     * 3. 发送 Redis 广播，通知其他节点删除 L1。
     * <p>
     * [Cluster Broadcast] Evicts a cache item.
     * 1. Delete L2 Redis.
     * 2. Delete local L1.
     * 3. Send Redis broadcast to notify other nodes to delete L1.
     *
     * @param multiCacheName 缓存配置的唯一名称。/ The unique name of the cache configuration.
     * @param keyParams      用于构建要清除的缓存键的动态参数。/ The dynamic parameters to build the final cache key to evict.
     */
    void evict(String multiCacheName, Object... keyParams);

    /**
     * 清除 L1 缓存项。
     * 通常用于响应 Redis 广播消息，或者仅想清理本地内存时使用。
     * <p>
     * Clears an L1 cache item.
     * Usually used in response to Redis broadcast messages, or when you only want to clear local memory.
     * @param multiCacheName 缓存配置的唯一名称。/ The unique name of the cache configuration.
     * @param keyParams      用于构建要清除的缓存键的动态参数。/ The dynamic parameters to build the final cache key to evict.
     */
    void evictL1(String multiCacheName, Object... keyParams);

    /**
     * 获取 L1 缓存的统计信息。
     * 包括命中率、驱逐数量等。
     * <p>
     * Get the statistics of the L1 cache.
     * Includes hit rate, eviction count, etc.
     *
     * @param multiCacheName 缓存名称 / Cache name
     */
    String getL1Stats(String multiCacheName);
}
