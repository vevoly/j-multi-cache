package io.github.vevoly.jmulticache.api;

import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JMultiCache 核心 API 接口。
 * 定义了多级缓存框架对外暴露的所有标准操作，包括单点查询、批量查询、并集查询、哈希查询以及缓存预热等。
 * <p>
 * The core API interface for JMultiCache.
 * Defines all standard operations exposed by the multi-level cache framework, including single-item fetch, batch fetch, union fetch, hash fetch, and cache preloading.
 *
 * @author vevoly
 */
public interface JMultiCache {

    // =================================================================
    // ======================== 单点查询 / Single Item Fetch ============
    // =================================================================

    /**
     * 根据完整的缓存 Key 获取单个数据。
     * 如果缓存未命中，则调用 dbLoader 加载数据并回填缓存。
     * <p>
     * Fetches a single data item based on the full cache key.
     * If the cache misses, it invokes the dbLoader to load data and repopulates the cache.
     *
     * @param fullKey  完整的缓存键 (包含命名空间)。/ The full cache key (including namespace).
     * @param dbLoader 数据库加载器，当缓存未命中时执行。/ The database loader to execute when cache misses.
     * @param <T>      返回数据的类型。/ The type of the returned data.
     * @return 缓存或数据库中的数据。/ The data from cache or database.
     */
    <T> T fetchData(String fullKey, Supplier<T> dbLoader);

    /**
     * 根据配置名称和动态参数获取单个数据。
     * 框架会自动根据配置中的 keyField 规则拼接 Key。
     * <p>
     * Fetches a single data item based on the configuration name and dynamic parameters.
     * The framework automatically constructs the key based on the keyField rule in the configuration.
     *
     * @param multiCacheName 缓存配置名称 (YML 中的 Key)。/ The cache configuration name (Key in YML).
     * @param dbLoader       数据库加载器。/ The database loader.
     * @param keyParams      用于拼接 Key 的动态参数。/ Dynamic parameters for constructing the key.
     * @param <T>            返回数据的类型。/ The type of the returned data.
     * @return 缓存或数据库中的数据。/ The data from cache or database.
     */
    <T> T fetchData(String multiCacheName, Supplier<T> dbLoader, String... keyParams);

    // =================================================================
    // ======================== 批量查询 / Batch Fetch ==================
    // =================================================================

    /**
     * 批量获取数据，并返回打平后的列表。
     * 适用于一对多的场景，但调用者只需要所有结果的集合。
     * <p>
     * Fetches data in a batch and returns a flattened list.
     * Suitable for one-to-many scenarios where the caller only needs a collection of all results.
     *
     * @param multiCacheName 缓存配置名称。/ The cache configuration name.
     * @param ids            查询 ID 集合。/ The collection of IDs to query.
     * @param businessKey    业务主键字段名，用于从 DB 结果中提取 ID 以进行映射。/ The business primary key field name, used to extract IDs from DB results for mapping.
     * @param queryFunction  批量回源查询函数。/ The batch source query function.
     * @param <K>            ID 的类型。/ The type of the ID.
     * @param <V>            返回列表中元素的类型。/ The type of elements in the returned list.
     * @return 打平后的数据列表。/ The flattened list of data.
     */
    <K, V> List<V> fetchMultiDataList(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction);

    /**
     * 批量获取数据，并返回分组后的 Map。
     * 适用于一对一或一对多场景，保留 ID 到数据的映射关系。
     * <p>
     * Fetches data in a batch and returns a grouped Map.
     * Suitable for one-to-one or one-to-many scenarios, preserving the mapping between IDs and data.
     *
     * @param multiCacheName 缓存配置名称。/ The cache configuration name.
     * @param ids            查询 ID 集合。/ The collection of IDs to query.
     * @param businessKey    业务主键字段名。/ The business primary key field name.
     * @param queryFunction  批量回源查询函数。/ The batch source query function.
     * @param <K>            ID 的类型。/ The type of the ID.
     * @param <V>            Map 值（可能是实体或实体的集合）的类型。/ The type of the Map value (can be an entity or a collection of entities).
     * @return 分组后的 Map。/ The grouped Map.
     */
    <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction);

    /**
     * 批量获取数据，返回分组 Map，支持自定义 Key 构建器。
     * 适用于 Key 生成规则较复杂（如 SpEL）的场景。
     * <p>
     * Fetches data in a batch and returns a grouped Map, supporting a custom Key Builder.
     * Suitable for scenarios with complex key generation rules (e.g., SpEL).
     *
     * @param multiCacheName 缓存配置名称。/ The cache configuration name.
     * @param ids            查询 ID 集合。/ The collection of IDs to query.
     * @param businessKey    业务主键字段名。/ The business primary key field name.
     * @param keyBuilder     自定义的 Key 构建函数。/ Custom key building function.
     * @param queryFunction  批量回源查询函数。/ The batch source query function.
     * @param <K>            ID 的类型。/ The type of the ID.
     * @param <V>            Map 值的类型。/ The type of the Map value.
     * @return 分组后的 Map。/ The grouped Map.
     */
    <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<K, String> keyBuilder, Function<Collection<K>, V> queryFunction);

    // =================================================================
    // ======================== 高级数据结构 / Advanced Data Structures ==
    // =================================================================

    /**
     * 获取多个 Redis Set 的并集。
     * 如果 L2 缓存未完全命中，则回源数据库并计算并集。
     * <p>
     * Fetches the union of multiple Redis Sets.
     * If the L2 cache is not fully hit, it queries the database and computes the union.
     *
     * @param setKeysInRedis  参与并集计算的 Redis Key 列表。/ List of Redis keys participating in the union calculation.
     * @param dbQueryFunction 数据库回源函数，输入为缺失的 Key 列表，输出为 Key 到 Set 的映射。/ Database fallback function, input is a list of missing keys, output is a map of Key to Set.
     * @param <T>             Set 中元素的类型。/ The type of elements in the Set.
     * @return 计算后的并集。/ The computed union set.
     */
    <T> Set<T> fetchUnionData(List<String> setKeysInRedis, Function<List<String>, Map<String, Set<T>>> dbQueryFunction);

    /**
     * 获取 Hash 结构中的单个字段。
     * 由于Hash不能单独设置item的缓存过期时间，所以无法进行空缓存回种。
     * 有缓存击穿的风险，建议使用其他更通用的存储策略替代。
     * <p>
     * Fetches a single field from a Hash structure.
     * Due to the inability to set the cache expiration time for individual items in a Hash, it is not possible to perform a empty cache planting back.
     * so, there is a risk of Cache Penetration. It is recommended to use other more general storage strategies instead.
     *
     * @param hashKey       Hash 的 Key。/ The Hash key.
     * @param field         字段名。/ The field name.
     * @param resultType    返回值的类型。/ The type of the return value.
     * @param queryFunction 回源查询函数。/ The fallback query function.
     * @param <T>           返回数据的类型。/ The type of the returned data.
     * @return 字段的值。/ The value of the field.
     */
    @Deprecated
    <T> T fetchHashData(String hashKey, String field, Class<T> resultType, Supplier<T> queryFunction);

}