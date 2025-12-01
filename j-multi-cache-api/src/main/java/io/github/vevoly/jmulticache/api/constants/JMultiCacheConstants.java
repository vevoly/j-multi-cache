package io.github.vevoly.jmulticache.api.constants;

/**
 * 框架中使用的所有公共常量的集合。
 * <p>
 * A collection of all public constants used within the framework.
 *
 * @author vevoly
 */
public interface JMultiCacheConstants {

    // ===================================================================
    // ====================== 空缓存相关常量 / Null Caching Constants ======================
    // ===================================================================

    /**
     * 用于表示缓存中“空值”的特殊标记字符串，以防止缓存穿透。
     * <p>
     * The special string marker used to represent a "null" value in the cache to prevent cache penetration.
     */
    String EMPTY_CACHE_VALUE = "J_MULTI_CACHE_NULL";

    /**
     * 空值标记在 Redis 中的默认过期时间（秒）。
     * <p>
     * The default expiration time (in seconds) for the null value marker in Redis.
     */
    long EMPTY_CACHE_REDIS_TTL = 60;

    // ===================================================================
    // =============== L1 缓存失效通知相关常量 / L1 Cache Invalidation Constants ===============
    // ===================================================================

    /**
     * 用于广播 L1 (Caffeine) 缓存失效消息的 Redis Pub/Sub 频道名称。
     * <p>
     * The name of the Redis Pub/Sub channel used for broadcasting L1 (Caffeine) cache invalidation messages.
     */
    String J_MULTI_CACHE_EVICT_TOPIC = "j_multi_cache:topic:evict";

    // ===================================================================
    // ====================== 全局默认配置值 / Global Default Values ======================
    // ===================================================================

    /**
     * 当未指定时，使用的默认缓存命名空间。
     * <p>
     * The default cache namespace to use when none is specified.
     */
    String DEFAULT_NAMESPACE = "j_multi_cache:default";

    /**
     * 缓存项在 Redis 中的默认过期时间（秒）。(1 小时)
     * <p>
     * The default expiration time (in seconds) for cache items in Redis. (1 hour)
     */
    long DEFAULT_REDIS_TTL = 3600L;

    /**
     * 缓存项在 L1 (Caffeine) 本地缓存中的默认过期时间（秒）。(30 秒)
     * <p>
     * The default expiration time (in seconds) for cache items in the L1 (Caffeine) local cache. (30 seconds)
     */
    long DEFAULT_LOCAL_TTL = 30L;

    /**
     * L1 (Caffeine) 本地缓存的默认最大容量。
     * <p>
     * The default maximum size for the L1 (Caffeine) local cache.
     */
    long DEFAULT_LOCAL_CACHE_MAX_SIZE = 100L;

    /**
     * 默认的 Redis 存储结构类型。
     * <p>
     * The default Redis storage structure type.
     */
    String DEFAULT_STORAGE_TYPE = DefaultStorageTypes.STRING;

    /**
     * 默认的缓存实体类型。
     * <p>
     * The default cache entity type.
     */
    Class<?> DEFAULT_ENTITY_CLASS = Object.class;

    /**
     * 默认的用于生成缓存 key 的 SpEL 表达式字段。
     * <p>
     * The default SpEL expression field used to generate cache keys.
     */
    String DEFAULT_KEY_FIELD = "#id";

    /**
     * 默认的缓存存储与回源策略。
     * <p>
     * The default cache storage and source-of-truth strategy.
     */
    String DEFAULT_STORAGE_POLICY = DefaultStoragePolicies.L1_L2_DB;

    /**
     * 缓存预热时，默认调用的数据获取方法名。
     * <p>
     * The default method name for data fetching during cache preloading.
     */
    String DEFAULT_FETCH_METHOD_NAME = "list";

    // ===================================================================
    // ===================== 特定方法契约常量 / Method Contract Constants ===
    // ===================================================================

    /**
     * {@code RedisClient.sunionAndFindMisses} 方法返回的 Map 中用于存储并集结果的 Key。
     * <p>
     * The key for the union result in the map returned by the {@code RedisClient.sunionAndFindMisses} method.
     */
    String UNION_RESULT = "unionResult";

    /**
     * {@code RedisClient.sunionAndFindMisses} 方法返回的 Map 中用于存储未命中 Key 列表的 Key。
     * <p>
     * The key for the list of missed keys in the map returned by the {@code RedisClient.sunionAndFindMisses} method.
     */
    String UNION_MISSED_KEYS = "missedKeys";

    // ====================================================================
    // ==================== 配置属性常量 / Configuration Property Constants =
    // ====================================================================

    /**
     * 用于标记是否预加载缓存的属性名。
     * <p>
     * The name of the attribute used to mark whether to preload the cache.
     */
    String PRELOAD_ATTRIBUTE_NAME = "preload";

    /**
     * Core 模块中 JMultiCacheEnableRegistrar 的全限定类名
     * <p>
     * Core module's fully qualified class name for JMultiCacheEnableRegistrar
     */
    String REGISTRAR_CLASS_NAME = "io.github.vevoly.jmulticache.core.config.JMultiCacheEnableRegistrar";

    /**
     * JMultiCacheMarkerConfiguration 的全限定名
     * <p>
     * JMultiCacheMarkerConfiguration's fully qualified class name
     */
    String MARKER_CONFIG_CLASS_NAME = "io.github.vevoly.jmulticache.core.config.JMultiCacheMarkerConfiguration";
}
