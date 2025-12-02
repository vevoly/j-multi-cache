package io.github.vevoly.jmulticache.core.redis.batch;

import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link BatchOperation} 接口基于 Redisson {@link RBatch} 的实现。
 * <p>
 * 此类将通用的批量操作定义，转换为具体的 Redisson 异步命令。
 * 它封装了 Redisson 的 {@code RBatch} 实例，并在 {@code execute()} 方法被调用时统一提交所有命令。
 * <p>
 * An implementation of the {@link BatchOperation} interface based on Redisson's {@link RBatch}.
 * This class translates generic batch operation definitions into specific Redisson asynchronous commands.
 * It encapsulates a Redisson {@code RBatch} instance and submits all commands at once when the {@code execute()} method is called.
 *
 * @author vevoly
 */
@RequiredArgsConstructor
public class RedissonBatchOperation implements BatchOperation {

    private final RBatch redissonBatch;

    // ===================================================================
    // =================== String / Object Operations ====================
    // ===================================================================

    @Override
    public CompletableFuture<Void> setAsync(String key, Object value, Duration ttl) {
        RFuture<Void> future;
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            future = redissonBatch.getBucket(key).setAsync(value, ttl);
        } else {
            future = redissonBatch.getBucket(key).setAsync(value);
        }
        return toCompletableFuture(future);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getAsync(String key) {
        RFuture<T> future = (RFuture<T>) redissonBatch.getBucket(key).getAsync();
        return future.toCompletableFuture();
    }

    // ===================================================================
    // ======================== List Operations ==========================
    // ===================================================================

    @Override
    public CompletableFuture<Void> listDeleteAsync(String key) {
        RFuture<Boolean> future = redissonBatch.getList(key).deleteAsync();
        return toCompletableFuture(future);
    }

    @Override
    public CompletableFuture<Void> listAddAllAsync(String key, Collection<?> values) {
        RFuture<Boolean> future = redissonBatch.getList(key).addAllAsync(values);
        return toCompletableFuture(future);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Object>> listGetAllAsync(String key) {
        RFuture<List<Object>> future = redissonBatch.getList(key).readAllAsync();
        return future.toCompletableFuture();
    }

    // ===================================================================
    // ======================== Set Operations ===========================
    // ===================================================================

    @Override
    public CompletableFuture<Void> setDeleteAsync(String key) {
        RFuture<Boolean> future = redissonBatch.getSet(key).deleteAsync();
        return toCompletableFuture(future);
    }

    @Override
    public CompletableFuture<Void> setAddAllAsync(String key, Collection<?> values) {
        RFuture<Boolean> future = redissonBatch.getSet(key).addAllAsync(values);
        return toCompletableFuture(future);
    }

    @Override
    public CompletableFuture<Void> setAddAllStringAsync(String key, Collection<?> values) {
        RFuture<Boolean> future = redissonBatch.getSet(key, StringCodec.INSTANCE).addAllAsync(values);
        return toCompletableFuture(future);
    }

    @Override
    public CompletableFuture<Void> setAddAsync(String key, Object value) {
        RFuture<Boolean> future = redissonBatch.getSet(key).addAsync(value);
        return toCompletableFuture(future);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Set<Object>> setGetAllAsync(String key) {
        RFuture<Set<Object>> future = redissonBatch.getSet(key).readAllAsync();
        return future.toCompletableFuture();
    }


    // ===================================================================
    // ======================== Hash Operations ==========================
    // ===================================================================

    @Override
    public CompletableFuture<Void> hashPutAllAsync(String key, Map<String, ?> map) {
        RFuture<Void> future = redissonBatch.getMap(key).putAllAsync(map);
        return future.toCompletableFuture();
    }

    // ===================================================================
    // ====================== Common Operations ==========================
    // ===================================================================

    @Override
    public CompletableFuture<Void> expireAsync(String key, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return CompletableFuture.completedFuture(null);
        }
        RFuture<Boolean> future = redissonBatch.getBucket(key).expireAsync(ttl);
        return toCompletableFuture(future);
    }

    @Override
    public CompletableFuture<Void> deleteAsync(String... keys) {
        if (keys == null || keys.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        RFuture<Long> future = redissonBatch.getKeys().deleteAsync(keys);
        return toCompletableFuture(future);
    }

    @Override
    public void zAddAsync(String key, Map<Object, Double> scoreMembers) {
        RScoredSortedSetAsync<Object> zset = redissonBatch.getScoredSortedSet(key);
        zset.addAllAsync(scoreMembers);
    }

    @Override
    public void execute() {
        // Redisson 的 execute 方法返回 BatchResult，但我们的接口是 void，所以直接调用即可。
        redissonBatch.execute();
    }

    /**
     * 将 Redisson 的 RFuture<Boolean> 或 RFuture<Long> 转换为 CompletableFuture<Void> 的私有辅助方法。
     * <p>
     * A private helper method to convert Redisson's RFuture<Boolean> or RFuture<Long> into a CompletableFuture<Void>.
     *
     * @param rFuture Redisson 的异步 Future。 / The asynchronous Future from Redisson.
     * @return a {@link CompletableFuture} that completes with null.
     */
    private CompletableFuture<Void> toCompletableFuture(RFuture<?> rFuture) {
        return rFuture.toCompletableFuture().thenApply(v -> null);
    }
}
