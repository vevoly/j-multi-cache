package io.github.vevoly.jmulticache.api.constants;

/**
 * 定义了框架内置的默认缓存存储与回源策略标识符。
 * <p>
 * Defines the built-in default cache storage and source-of-truth strategy identifiers.
 *
 * @author vevoly
 */
public final class DefaultStoragePolicies {

    /**
     * 私有构造函数，防止实例化。
     * <p>
     * Private constructor to prevent instantiation.
     */
    private DefaultStoragePolicies() {}

    /**
     * 三级缓存策略：L1 (本地缓存) -> L2 (Redis) -> DB (数据源)。
     * <p>
     * 这是最完整的缓存模式，提供了最高的读取性能。适用于读多写少的场景。
     * 当 L2 缓存命中时，会自动回填 L1 缓存。
     * <p>
     * Three-level cache strategy: L1 (Local Cache) -> L2 (Redis) -> DB (Data Source).
     * This is the most comprehensive caching mode, providing the highest read performance. Suitable for read-heavy scenarios.
     * When an L2 cache hit occurs, the L1 cache is automatically populated.
     */
    public static final String L1_L2_DB = "L1_L2_DB";

    /**
     * 两级缓存策略：L2 (Redis) -> DB (数据源)。
     * <p>
     * 适用于不希望使用 JVM 堆内存作为本地缓存，但仍需要分布式缓存加速的场景。
     * <p>
     * Two-level cache strategy: L2 (Redis) -> DB (Data Source).
     * Suitable for scenarios where using JVM heap memory as a local cache is not desired, but distributed caching is still needed for acceleration.
     */
    public static final String L2_DB = "L2_DB";

    /**
     * 仅一级缓存策略：L1 (本地缓存) -> DB (数据源)。
     * <p>
     * 此模式下不使用 Redis。适用于单体应用或对数据一致性要求不高的分布式场景。
     * <p>
     * L1-only cache strategy: L1 (Local Cache) -> DB (Data Source).
     * Redis is not used in this mode. Suitable for monolithic applications or distributed scenarios with low data consistency requirements.
     */
    public static final String L1_DB = "L1_DB";

    /**
     * 仅数据库策略
     * <p>
     * Database only.
     */
    public static final String DB_ONLY = "DB";

}