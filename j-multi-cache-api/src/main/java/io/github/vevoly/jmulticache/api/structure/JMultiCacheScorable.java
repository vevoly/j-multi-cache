package io.github.vevoly.jmulticache.api.structure;

/**
 * ZSet Scorable Object Contract.
 * <p>
 * This interface defines the contract for objects stored in Redis ZSet.
 * When the <code>storage-type</code> is configured as <code>zset</code>,
 * the corresponding <code>entity-class</code> must implement this interface.
 * The framework uses the provided methods to read and write the Redis ZSet
 * member (ID) and score.
 * </p>
 *
 * <p>
 * ZSet 可排序对象契约接口。
 * 当 <code>storage-type</code> 配置为 <code>zset</code> 时，
 * 对应的 <code>entity-class</code> 必须实现本接口。
 * 框架将依赖本接口提供的方法读写 Redis ZSet 的 member（ID）与 score（分值）。
 * </p>
 *
 * @author vevoly
 */
public interface JMultiCacheScorable {

    /**
     * Returns the value used as the Redis ZSet member (usually the ID).
     * This value must be unique and serializable as a string.
     *
     * 返回用于存储到 Redis ZSet 中的 member 值（通常为唯一 ID）。
     * 该值需保证唯一性并可序列化为字符串。
     *
     * @return the member string stored in Redis ZSet
     *         用于存储到 Redis ZSet 的字符串 ID
     */
    String getCacheId();

    /**
     * Returns the score used for sorting within Redis ZSet.
     * Higher or lower values determine the ranking depending on the sorting logic.
     *
     * 返回在 Redis ZSet 中用于排序的 score 分值。
     * 分值的大小决定排序顺序，取决于具体业务规则。
     *
     * @return the numeric score used for ZSet ranking
     *         用于 ZSet 排序的数值分数
     */
    Double getCacheScore();

    /**
     * Sets the ID (member value) when the object is reconstructed from Redis.
     * The framework invokes this method during deserialization or hydration.
     *
     * 在从 Redis 读取对象时回填 ID（member）。
     * 框架在对象反序列化或数据回填阶段调用此方法。
     *
     * @param id the ZSet member value read from Redis
     *           从 Redis 中读取的 ZSet member 值
     */
    void setCacheId(String id);

    /**
     * Sets the score when the object is reconstructed from Redis.
     * The framework invokes this method during deserialization or hydration.
     *
     * 在从 Redis 读取对象时回填 score 值。
     * 框架在对象反序列化或数据回填阶段调用此方法。
     *
     * @param score the numeric score read from Redis
     *              从 Redis 中读取的 score 分值
     */
    void setCacheScore(Double score);
}