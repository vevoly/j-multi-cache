package io.github.vevoly.jmulticache.api.redis;

import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.structure.JMultiCacheScoredEntry;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存框架对 Redis 操作的统一客户端接口。
 * <p>
 * 该接口定义了框架所需的所有 Redis 基本操作，旨在屏蔽底层具体 Redis 客户端（如 Redisson, Jedis, Lettuce）的实现细节。
 * 框架的所有组件都应依赖此接口，而不是具体的实现。
 * <p>
 * This interface defines all the basic Redis operations required by the framework,
 * aiming to abstract away the implementation details of the underlying Redis client (e.g., Redisson, Jedis, Lettuce).
 * All components of the framework should depend on this interface rather than a specific implementation.
 *
 * @author vevoly
 */
public interface RedisClient {

    // ==================================================================
    // ============ 通用 Key 操作 / Common Key Operations =================
    // ===================================================================

    /**
     * 检查给定的 key 是否存在。
     * <p>
     * Checks if a given key exists.
     *
     * @param key 键 / the key
     * @return {@code true} 如果 key 存在 / {@code true} if the key exists
     */
    boolean exists(String key);

    /**
     * 删除一个或多个 key。
     * <p>
     * Deletes one or more keys.
     *
     * @param keys 要删除的 key 数组 / array of keys to delete
     */
    void delete(String... keys);

    /**
     * 删除一个 key 集合。
     * <p>
     * Deletes a collection of keys.
     *
     * @param keys 要删除的 key 集合 / collection of keys to delete
     */
    void delete(Collection<String> keys);

    /**
     * 设置 key 的过期时间。
     * <p>
     * Sets a timeout on a key.
     *
     * @param key     键 / the key
     * @param timeout 过期时间 / the timeout
     */
    void expire(String key, Duration timeout);

    /**
     * 获取 Key 的存储类型。
     * 对应 Redis 命令: TYPE key
     *
     * @param key 缓存 Key
     * @return 类型字符串 (例如: "string", "list", "set", "zset", "hash", "none")
     */
    String type(String key);

    // ===================================================================
    // ======== String / Object 操作 / String or Object Operations ========
    // ===================================================================

    /**
     * 获取指定 key 的值。
     * <p>
     * Gets the value of a specified key.
     *
     * @param key 键 / the key
     * @return key 对应的值，如果 key 不存在则返回 null / the value of the key, or null if the key does not exist
     */
    <T> T get(String key);

    /**
     * 批量获取多个 key 的值。
     * <p>
     * Gets the values of multiple keys.
     * @param keys 要查询的 key 集合 / collection of keys to query
     * @return 一个 Map，Key 是 Redis Key，Value 是对应的值 / A map where keys are Redis keys and values are the corresponding values.
     */
    Map<String, Object> mget(Collection<String> keys);

    /**
     * 设置 key 的值为 value。
     * <p>
     * Sets the value of a key.
     *
     * @param key     键 / the key
     * @param value   值 / the value
     * @param timeout 过期时间 / the expiration timeout
     */
    void set(String key, Object value, Duration timeout);

    /**
     * 批量设置多个 key-value 对。
     * <p>
     * Sets multiple key-value pairs.
     * @param data 要设置的 key-value Map / A map of key-value pairs to set.
     * @param timeout 统一的过期时间 / The uniform expiration timeout for all keys.
     */
    void mset(Map<String, Object> data, Duration timeout);

    // ===================================================================
    // ======================== List 操作 / List Operations ===============
    // ===================================================================

    /**
     * 获取列表 key 的所有元素。
     * <p>
     * Gets all elements from the list at the specified key.
     *
     * @param key 键 / the key
     * @return 包含列表所有元素的 List / a List containing all elements of the list
     */
    <T> List<T> getList(String key);

    /**
     * 用给定的列表覆盖 key 处的内容。
     * <p>
     * Overwrites the content at the specified key with the given list.
     *
     * @param key     键 / the key
     * @param value   要写入的 List / the List to write
     * @param timeout 过期时间 / the expiration timeout
     */
    void setList(String key, List<?> value, Duration timeout);

    // ===================================================================
    // ======================== Set 操作 / Set Operations =================
    // ===================================================================

    /**
     * 获取集合 key 的所有成员。
     * <p>
     * Gets all members of the set at the specified key.
     *
     * @param key 键 / the key
     * @return 包含集合所有成员的 Set / a Set containing all members of the set
     */
    <T> Set<T> getSet(String key);

    /**
     * 向集合 key 中添加一个或多个成员。
     * <p>
     * Adds one or more members to the set at the specified key.
     *
     * @param key     键 / the key
     * @param timeout 过期时间 / the expiration timeout
     * @param members 要添加的成员 / members to add
     */
    void sAdd(String key, Duration timeout, Object... members);

    /**
     * 使用 Lua 脚本高效地计算多个 Set 的并集，并找出其中不存在的 Key。
     * <p>
     * Efficiently computes the union of multiple sets using a Lua script and identifies which keys do not exist.
     *
     * @param keys 要操作的 Set 的 Key 列表 / a list of keys for the sets to operate on
     * @return 一个 Map，包含 "unionResult" (存在的 key 的并集结果) 和 "missedKeys" (不存在的 key 列表) /
     *         A Map containing "unionResult" (the union of existing keys) and "missedKeys" (the list of non-existent keys).
     */
    Map<String, Object> sunionAndFindMisses(List<String> keys);

    // ===================================================================
    // ======================== ZSet 操作 / ZSet Operations ===============
    // ===================================================================

    /**
     * 获取 ZSet 范围数据，包含分数。
     * <p>
     * Get ZSet range data, including scores.
     *
     * @param key 键 / the key
     * @param start 起始索引 / the start index
     */
    Collection<JMultiCacheScoredEntry<String>> zRangeWithScores(String key, int start, int end);

    /**
     * 添加 ZSet 数据。
     * <p>
     * Add ZSet data.
     *
     * @param key 键 / the key
     * @param scoreMembers 分数和成员的映射 / a map of scores and members
     * @param ttl 过期时间 / the expiration time
     */
    void zAdd(String key, Map<Object, Double> scoreMembers, Duration ttl);

    // ===================================================================
    // ======================== Hash 操作 / Hash Operations ===============
    // ===================================================================

    /**
     * 获取存储在哈希表中指定字段的值。
     * <p>
     * Gets the value of a hash field.
     *
     * @param key   键 / the key
     * @param field 字段 / the field
     * @return 字段的值，如果 key 或 field 不存在则返回 null / the value of the field, or null if the key or field does not exist
     */
    <T> T hget(String key, String field);

    /**
     * 获取哈希表中所有的键值对。
     * <p>
     * Gets all fields and values of a hash.
     *
     * @param key 键 / the key
     * @return 包含哈希表所有内容的 Map / a Map containing all fields and values of the hash
     */
    <K, V> Map<K, V> hgetAll(String key);

    /**
     * 将哈希表 key 中字段 field 的值设为 value。
     * <p>
     * Sets the value of a hash field.
     *
     * @param key     键 / the key
     * @param field   字段 / the field
     * @param value   值 / the value
     * @param timeout 整个哈希表的过期时间 / the expiration timeout for the entire hash
     */
    void hset(String key, String field, Object value, Duration timeout);

    /**
     * 同时将多个“字段-值”对设置到哈希表中。
     * <p>
     * Sets multiple field-value pairs in a hash.
     *
     * @param key     键 / the key
     * @param map     包含多个“字段-值”对的 Map / a Map containing multiple field-value pairs
     * @param timeout 整个哈希表的过期时间 / the expiration timeout for the entire hash
     */
    void hmset(String key, Map<String, Object> map, Duration timeout);

    // ===================================================================
    // ===================== 分布式锁 / Distributed Lock ==================
    // ===================================================================

    /**
     * 尝试获取一个分布式锁。
     * <p>
     * Tries to acquire a distributed lock.
     *
     * @param lockKey   锁的唯一键 / the unique key for the lock
     * @param waitTime  最长等待时间 / the maximum time to wait for the lock
     * @param leaseTime 锁的持有时间（自动释放时间） / the time to hold the lock (lease time)
     * @param unit      时间单位 / the time unit
     * @return {@code true} 如果成功获取锁 / {@code true} if the lock was acquired successfully
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 释放一个分布式锁。
     * <p>
     * Releases a distributed lock.
     *
     * @param lockKey 锁的唯一键 / the unique key for the lock
     */
    void unlock(String lockKey);

    // ===================================================================
    // =================== 发布/订阅 / Publish/Subscribe ==================
    // ===================================================================

    /**
     * 向指定频道发布一条消息。
     * <p>
     * Publishes a message to a channel.
     *
     * @param channel 频道名称 / the name of the channel
     * @param message 消息对象 / the message object
     */
    void publish(String channel, Object message);

    // ===================================================================
    // =================== 批量操作会话 / Batch operation session ==========
    // ===================================================================

    /**
     * 创建一个新的批量操作会话。
     * <p>
     * Creates a new batch operation session.
     * @return 一个 {@link BatchOperation} 实例。 / A {@link BatchOperation} instance.
     */
    BatchOperation createBatchOperation();
}
