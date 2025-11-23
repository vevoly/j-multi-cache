package io.github.vevoly.jmulticache.api.config;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;


import java.time.Duration;

/**
 * 归一化后的内部配置对象。
 * <p>
 * 此对象是 YML 配置、代码级配置（如注解覆盖）以及框架默认值合并后的最终、不可变的结果。
 * 框架的所有底层组件，如缓存管理器和存储策略，都只依赖这个标准对象来获取配置信息。
 * <p>
 * The normalized internal configuration object.
 * This object is the final, immutable result of merging YML configurations, code-level configurations (e.g., annotation overrides), and framework defaults.
 * All underlying components of the framework, such as the cache manager and storage strategies, rely solely on this standard object for configuration information.
 *
 * @author vevoly
 */
@Getter
@Builder
@ToString
@AllArgsConstructor
public final class ResolvedJMultiCacheConfig {

    // ===================================================================
    // ======================= 核心配置属性 / Core Properties ==============
    // ===================================================================

    /**
     * 配置的唯一名称，对应于 YML 文件中的顶级 Key。
     * <p>
     * The unique name of the configuration, corresponding to the top-level key in the YML file.
     */
    private final String name;

    /**
     * 缓存的命名空间，作为 Redis Key 的前缀。
     * <p>
     * The cache namespace, used as a prefix for Redis keys.
     */
    private final String namespace;

    /**
     * 缓存的实体对象的 Class 类型。
     * <p>
     * The Class type of the cached entity object.
     */
    private final Class<?> entityClass;

    // ===================================================================
    // ======================= 派生配置属性 / Derived Properties ===========
    // ===================================================================

    /**
     * 缓存的存储与回源策略字符串。
     * <p>
     * The cache storage and source-of-truth strategy string.
     */
    @Builder.Default
    private final String storagePolicy = JMultiCacheConstants.DEFAULT_STORAGE_POLICY;

    /**
     * 经过 Jackson 解析后的、用于精确反序列化的 TypeReference。
     * <p>
     * The TypeReference, parsed by Jackson, for precise deserialization.
     */
    private final TypeReference<?> typeReference;

    // ===================================================================
    // ======================= 行为配置属性 / Behavior Properties ==========
    // ===================================================================

    /**
     * 缓存项在 Redis 中的过期时间。
     * <p>
     * The expiration time for cache items in Redis.
     */
    @Builder.Default
    private final Duration redisTtl = Duration.ofSeconds(JMultiCacheConstants.DEFAULT_REDIS_TTL);

    /**
     * 缓存项在 L1 本地缓存中的过期时间。
     * <p>
     * The expiration time for cache items in the L1 local cache.
     */
    @Builder.Default
    private final Duration localTtl = Duration.ofSeconds(JMultiCacheConstants.DEFAULT_LOCAL_TTL);

    /**
     * L1 本地缓存的最大容量。
     * <p>
     * The maximum size of the L1 local cache.
     */
    @Builder.Default
    private final Long localMaxSize = JMultiCacheConstants.DEFAULT_LOCAL_CACHE_MAX_SIZE;

    /**
     * 缓存存储类型 (STRING, LIST, SET, HASH, PAGE, 或自定义类型)。
     * <p>
     * The cache storage type (STRING, LIST, SET, HASH, PAGE, or a custom type).
     */
    @Builder.Default
    private final String storageType = JMultiCacheConstants.DEFAULT_STORAGE_TYPE;

    /**
     * 用于从缓存目标对象中提取 Key 的 SpEL 表达式。
     * <p>
     * The SpEL expression used to extract the key from the cached target object.
     */
    @Builder.Default
    private final String keyField = JMultiCacheConstants.DEFAULT_KEY_FIELD;

    /**
     * 业务主键字段名，主要用于批量查询。
     * <p>
     * The business key field name, mainly used for batch queries.
     */
    @Builder.Default
    private final String businessKey = "";

    /**
     * 空值标记在 Redis 中的过期时间。
     * <p>
     * The expiration time for the null value marker in Redis.
     */
    @Builder.Default
    private final Duration emptyCacheTtl = Duration.ofSeconds(JMultiCacheConstants.EMPTY_CACHE_REDIS_TTL);

    /**
     * 用于表示缓存中“空值”的特殊标记字符串。
     * <p>
     * The special string marker used to represent a "null" value in the cache.
     */
    @Builder.Default
    private final String emptyValueMark = JMultiCacheConstants.EMPTY_CACHE_VALUE;

    // ===================================================================
    // ======================= 辅助方法 / Helper Methods ==================
    // ===================================================================

    /**
     * 根据当前的 {@code storagePolicy} 判断是否应使用 L1 (本地) 缓存。
     * <p>
     * Determines whether to use the L1 (local) cache based on the current {@code storagePolicy}.
     *
     * @return {@code true} 如果策略包含 "L1" / {@code true} if the policy contains "L1".
     */
    public boolean isUseL1() {
        return storagePolicy != null && storagePolicy.contains("L1");
    }

    /**
     * 根据当前的 {@code storagePolicy} 判断是否应使用 L2 (Redis) 缓存。
     * <p>
     * Determines whether to use the L2 (Redis) cache based on the current {@code storagePolicy}.
     *
     * @return {@code true} 如果策略包含 "L2" / {@code true} if the policy contains "L2".
     */
    public boolean isUseL2() {
        return storagePolicy != null && storagePolicy.contains("L2");
    }

    /**
     * 根据当前的 {@code storagePolicy} 判断当 L2 缓存命中时，是否应该回填 L1 缓存。
     * <p>
     * 默认约定：只要同时使用 L1 和 L2，就进行回填。
     * <p>
     * Determines whether the L1 cache should be populated on an L2 cache hit, based on the current {@code storagePolicy}.
     * Default convention: populate if both L1 and L2 are used.
     *
     * @return {@code true} 如果同时使用 L1 和 L2 / {@code true} if both L1 and L2 are used.
     */
    public boolean isPopulateL1FromL2() {
        return isUseL1() && isUseL2();
    }
}
