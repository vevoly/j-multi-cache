package io.github.vevoly.jmulticache.api.strategy;

import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.redis.RedisClient;

import java.time.Duration;

/**
 * 一个扩展接口，为支持基于字段(field)操作的存储策略（如 Redis Hash）提供契约。
 * <p>
 * An extension interface that provides a contract for storage strategies that support field-based operations, such as Redis Hash.
 *
 * @param <V> 字段值的类型。/ The type of the field's value.
 * @author vevoly
 */
public interface FieldBasedStorageStrategy<V> {

    /**
     * 从一个集合型缓存（如 Hash）中读取单个字段的值。
     * <p>
     * Reads the value of a single field from a collection-type cache (e.g., a Hash).
     *
     * @param redisClient     Redisson 客户端。/ The Redisson client.
     * @param key             Redis 的主键。/ The main key in Redis.
     * @param field           要获取的字段。/ The field to get.
     * @param fieldType       字段值的 Class 类型，用于反序列化。/ The Class type of the field's value for deserialization.
     * @param config          已解析过的配置信息。/ The resolved configuration information.
     * @return 字段对应的值，如果不存在则为 null。/ The value of the field, or null if it does not exist.
     */
    V readField(RedisClient redisClient, String key, String field, Class<V> fieldType, ResolvedJMultiCacheConfig config);

    /**
     * 向一个集合型缓存（如 Hash）中写入单个字段的值。
     * <p>
     * Writes the value of a single field to a collection-type cache (e.g., a Hash).
     *
     * @param redisClient     Redisson 客户端。/ The Redisson client.
     * @param key             Redis 的主键。/ The main key in Redis.
     * @param field           要设置的字段。/ The field to set.
     * @param value           要设置的值。/ The value to set.
     * @param config          已解析过的配置信息。/ The resolved configuration information.
     */
    void writeField(RedisClient redisClient, String key, String field, V value, ResolvedJMultiCacheConfig config);
}
