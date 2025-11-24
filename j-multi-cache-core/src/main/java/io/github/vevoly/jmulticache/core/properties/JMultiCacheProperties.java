package io.github.vevoly.jmulticache.core.properties;

import lombok.Data;
import java.time.Duration;

/**
 * 映射 application.yml 文件中单个缓存配置块的属性。
 * <p>
 * Maps the properties of a single cache configuration block from the application.yml file.
 *
 * @author vevoly
 */
@Data
public class JMultiCacheProperties {

    /**
     * 缓存的命名空间，作为 Redis Key 的前缀。这是必填项。
     * <p>
     * The cache namespace, used as a prefix for Redis keys. This is a required property.
     */
    private String namespace;

    /**
     * 缓存项在 Redis 中的过期时间（例如: 30s, 5m, 1h, 2d）。
     * <p>
     * The expiration time for cache items in Redis (e.g., 30s, 5m, 1h, 2d).
     */
    private Duration redisTtl;

    /**
     * 缓存项在 L1 本地缓存中的过期时间。如果为 null 或 0，则该缓存不使用 L1 缓存。
     * <p>
     * The expiration time for cache items in the L1 local cache. If null or 0, L1 cache is disabled for this cache.
     */
    private Duration localTtl;

    /**
     * L1 本地缓存的最大容量。
     * <p>
     * The maximum size of the L1 local cache.
     */
    private Long localMaxSize;

    /**
     * 缓存存储类型 (STRING, LIST, SET, HASH, PAGE, 或自定义类型)。
     * <p>
     * The cache storage type (STRING, LIST, SET, HASH, PAGE, or a custom type).
     */
    private String storageType;

    /**
     * 缓存的存储与回源策略 (例如: L1_L2_DB, L2_DB)。
     * <p>
     * The cache storage and source-of-truth strategy (e.g., L1_L2_DB, L2_DB).
     */
    private String storagePolicy;

    /**
     * 缓存的实体对象的完整类名 (例如: com.example.MyEntity)。这是必填项。
     * <p>
     * The fully qualified class name of the cached entity object (e.g., com.example.MyEntity). This is a required property.
     */
    private String entityClass;

    /**
     * 用于从缓存目标对象中提取 Key 的 SpEL 表达式。
     * <p>
     * The SpEL expression used to extract the key from the cached target object.
     */
    private String keyField;

    /**
     * 业务主键字段名，主要用于批量查询的场景。
     * <p>
     * The business key field name, mainly used in batch query scenarios.
     */
    private String businessKey;

    /**
     * 用于表示缓存中“空值”的自定义标记字符串。
     * <p>
     * A custom string marker used to represent a "null" value in the cache.
     */
    private String emptyCacheValue;

    /**
     * 空值标记在 Redis 中的自定义过期时间。
     * <p>
     * A custom expiration time for the null value marker in Redis.
     */
    private Duration emptyCacheTtl;
}
