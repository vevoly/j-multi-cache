package io.github.vevoly.jmulticache.api.structure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ZSet 成员与分数的包装类。
 * 用于解耦具体 Redis 客户端（如 Redisson）的实现依赖。
 *
 * ZSet member and score wrapper class.
 * Used to decouple the specific Redis client implementation dependencies (e.g., Redisson).
 *
 * @author vevoly
 */
@Data
@NoArgsConstructor
@AllArgsConstructor

public class JMultiCacheScoredEntry<V> implements Serializable {

    private V value;
    private Double score;

}
