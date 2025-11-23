package io.github.vevoly.jmulticache.api.strategy;

import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.wrap.UnionReadResult;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 定义了如何将 Java 对象与 Redis 中的特定数据结构进行相互读写的策略接口。
 * 框架的使用者可以通过实现此接口来增加对新 Redis 数据结构的支持。
 * <p>
 * Defines the strategy interface for reading and writing Java objects to and from specific Redis data structures.
 * Users of the framework can implement this interface to add support for new Redis data structures.
 *
 * @param <T> 此策略主要处理的数据类型 (例如, String, List, Set)。 / The primary data type this strategy handles (e.g., String, List, Set).
 * @author vevoly
 */
public interface RedisStorageStrategy<T> {

    /**
     * 返回当前策略的唯一标识符字符串 (约定使用全大写)。
     * <p>
     * 框架内置的类型由 {@link DefaultStorageTypes} 定义。
     * 自定义策略应返回一个独特的、非内置的字符串。
     * <p>
     * Returns the unique identifier string for this strategy (conventionally in uppercase).
     * Built-in types are defined in {@link DefaultStorageTypes}.
     * Custom strategies should return a unique, non-built-in string.
     *
     * @return 存储类型的唯一标识符。/ The unique identifier for the storage type.
     */
    String getStorageType();

    /**
     * 从 Redis 中读取单个键的值。
     * <p>
     * Reads the value of a single key from Redis.
     *
     * @param redisClient     Redisson 客户端。/ The Redisson client.
     * @param key             Redis 键。/ The Redis key.
     * @param typeRef         期望返回类型的 TypeReference，用于精确的反序列化。/ The TypeReference of the expected return type for accurate deserialization.
     * @param config          已解析过的配置信息。/ The resolved configuration information.
     * @return 查询到的数据，如果不存在则为 null。/ The queried data, or null if it does not exist.
     */
    T read(RedisClient redisClient, String key, TypeReference<T> typeRef, ResolvedJMultiCacheConfig config);

    /**
     * 异步批量从 Redis 读取多个键的值。
     * 实现者需要将 Redis 的批量操作添加到传入的 `RBatch` 对象中，并返回一个包含 CompletableFuture 的 Map。
     * <p>
     * Asynchronously reads the values of multiple keys from Redis in a batch.
     * Implementers need to add Redis batch operations to the provided `RBatch` object and return a map containing CompletableFutures.
     *
     * @param batch      Redisson 的 RBatch 对象。/ The RBatch object from Redisson.
     * @param keysToRead 需要读取的 Redis 键列表。/ The list of Redis keys to read.
     * @param typeRef    期望返回类型的 TypeReference。/ The TypeReference of the expected return type.
     * @param config     已解析过的配置信息。/ The resolved configuration information.
     * @param <V>        最终值的类型。/ The type of the final value.
     * @return 一个 Map，键是 Redis Key，值是包含了最终类型数据的 CompletableFuture<Optional<V>>。/ A map where the key is the Redis Key and the value is a CompletableFuture<Optional<V>> containing the final typed data.
     */
    <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config);

    /**
     * 将单个键值对写入 Redis。
     * <p>
     * Writes a single key-value pair to Redis.
     *
     * @param redisClient     Redisson 客户端。/ The Redisson client.
     * @param key             Redis 键。/ The Redis key.
     * @param value           要写入的数据。/ The value to write.
     * @param config          已解析过的配置信息。/ The resolved configuration information.
     */
    void write(RedisClient redisClient, String key, T value, ResolvedJMultiCacheConfig config);

    /**
     * 批量将多个键值对写入 Redis。
     * 实现者需要将 Redis 的批量写入操作添加到传入的 `RBatch` 对象中。
     * <p>
     * Writes multiple key-value pairs to Redis in a batch.
     * Implementers need to add Redis batch write operations to the provided `RBatch` object.
     *
     * @param batch       Redisson 的 RBatch 对象。/ The RBatch object from Redisson.
     * @param dataToCache 键是 Redis Key，值是要缓存的 Java 对象。/ A map where the key is the Redis Key and the value is the Java object to cache.
     * @param config      已解析过的配置信息。/ The resolved configuration information.
     */
    void writeMulti(BatchOperation batch, Map<String, T> dataToCache, ResolvedJMultiCacheConfig config);

    /**
     * 批量向 Redis 中写入“空值标记”，用于防止缓存穿透。
     * 当某些 Key 的真实数据不存在时，通过写入一个短期有效的“空值”占位符，
     * 可以避免这些 Key 被重复查询数据库，从而降低数据库压力并防止缓存穿透攻击。
     * <p>
     * Batch writing empty-value markers into Redis to prevent cache penetration.
     * When some keys correspond to non-existent data, writing a temporary
     * "empty-value placeholder" prevents repeated database hits for the same keys,
     * effectively reducing DB load and mitigating cache-penetration attacks.
     *
     * @param batch            Redisson 的 {@link BatchOperation} 对象，用于批量执行 Redis 指令。/ The {@link BatchOperation} object from Redisson, used to execute Redis commands in batch.
     * @param keysToMarkEmpty  需要写入“空值标记”的 Redis Key 列表。/ The list of Redis keys that should be marked with an empty-value placeholder.
     * @param config           已解析过的配置信息。/ The resolved configuration information.
     *                         可从中获取自定义的 {@code emptyValueMark}（空值标记内容）和 {@code emptyCacheTtl}（空值 Key 的过期时间）。
     *                         The fully-resolved cache configuration for this operation. Used to obtain the custom {@code emptyValueMark} (placeholder value)
     *                         and {@code emptyCacheTtl} (TTL of the empty-value keys).
     */
    void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config);

    /**
     * 从 Redis 中读取多个 Set 的并集，并识别出不存在的 Key。
     * <p>
     * Reads the union of multiple sets from Redis and identifies non-existent keys.
     *
     * @param redisClient  多级缓存框架的 Redis 客户端接口。/ The Redis client interface for the multi-cache framework.
     * @param keys         要进行并集计算的 Redis Key 列表。/ A list of Redis keys for the sets to be unioned.
     * @param typeRef      结果集中元素类型的 {@link TypeReference}，例如 {@code new TypeReference<Set<Long>>()}}。/
     *                     The {@link TypeReference} for the element type of the result set, e.g., {@code new TypeReference<Set<Long>>()}}.
     * @param config          已解析过的配置信息。/ The resolved configuration information.
     * @param <E>          结果集中元素的类型。/ The type of elements in the result set.
     * @return             一个 {@link UnionReadResult} 对象，它封装了并集计算的结果和未在 Redis 中找到的 Key 列表。/
     *                     A {@link UnionReadResult} object that encapsulates the union result and the list of keys not found in Redis.
     * @throws UnsupportedOperationException 如果当前存储策略不支持此操作。/ if the current storage strategy does not support this operation.
     */
    default <E> UnionReadResult<E> readUnion(RedisClient redisClient, List<String> keys, TypeReference<Set<E>> typeRef, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException(getStorageType() + " storage strategy does not support readUnion operation.");
    }
}
