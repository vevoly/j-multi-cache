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

    // =================================================================
    // ======================== 运维管理 / Operations ===================
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
     * [集群广播] 清空指定缓存配置下的【所有】数据。
     * 危险操作！
     * Clears all data under the specified cache configuration.
     * <p>
     * [Cluster broadcast] Clear all data under the specified cache configuration.
     * Danger!
     */
    void clear(String multiCacheName);

    /**
     * 获取指定缓存下的所有 Redis Key。
     * (扫描 L2 Redis)
     * <p>
     * Get all Redis keys under the specified cache.
     * (Scan L2 Redis)
     * @param multiCacheName 缓存配置的唯一名称。/ The unique name of the cache configuration.
     * @return Key 集合
     */
    Set<String> keys(String multiCacheName);

    // =================================================================
    // ======================== 开发辅助 / Dev Tools ====================
    // =================================================================

    /**
     * 生成缓存配置枚举类文件。
     * 根据 application.yml 中的配置，自动生成包含所有 configName 的 Java 枚举类。
     * 方便用户在代码中以强类型方式引用缓存名。
     * <p>
     * Generate a cache configuration enum class file.
     * Automatically generate a Java enum class containing all configName based on the configuration in application.yml.
     * Convenient for users to reference the cache name in code with strong type.
     *
     * @param packageName 生成类的包名 (e.g., "com.example.constant")
     * @param className   类名 (e.g., "CacheNames")
     * @param targetDir   源码根目录路径 (e.g., "src/main/java")
     */
    void generateEnumClass(String packageName, String className, String targetDir);

    // =================================================================
    // ======================== 监控统计 / Monitoring ===================
    // =================================================================

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
