package io.github.vevoly.jmulticache.api.redis.batch;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 定义了与实现无关的 Redis 批量/管道操作的抽象接口。
 * <p>
 * Defines an implementation-agnostic abstract interface for Redis batch/pipeline operations.
 *
 * @author vevoly
 */
public interface BatchOperation {

    // --- String / Object Operations ---
    CompletableFuture<Void> setAsync(String key, Object value, Duration ttl);
    <T> CompletableFuture<T> getAsync(String key);

    // --- List Operations ---
    CompletableFuture<Void> listDeleteAsync(String key);
    CompletableFuture<Void> listAddAllAsync(String key, Collection<?> values);
    <T> CompletableFuture<List<Object>> listGetAllAsync(String key);

    // --- Set Operations ---
    CompletableFuture<Void> setDeleteAsync(String key);
    CompletableFuture<Void> setAddAllAsync(String key, Collection<?> values);
    CompletableFuture<Void> setAddAllStringAsync(String key, Collection<?> values);
    CompletableFuture<Void> setAddAsync(String key, Object value);
    <T> CompletableFuture<Set<Object>> setGetAllAsync(String key);

    // --- Hash Operations ---
    CompletableFuture<Void> hashPutAllAsync(String key, Map<String, ?> map);

    // --- Common Operations ---
    CompletableFuture<Void> expireAsync(String key, Duration ttl);
    CompletableFuture<Void> deleteAsync(String... keys);

    /**
     * 批量添加 ZSet 元素 (ZADD)。
     *
     * @param key          Redis Key
     * @param scoreMembers 成员与分数的映射 Map<Member, Score>
     */
    void zAddAsync(String key, Map<Object, Double> scoreMembers);

    /**
     * 执行所有已缓存的批量命令。
     * <p>
     * Executes all cached batch commands.
     */
    void execute();
}
