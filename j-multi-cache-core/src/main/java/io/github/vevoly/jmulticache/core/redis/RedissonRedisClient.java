package io.github.vevoly.jmulticache.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.core.redis.batch.RedissonBatchOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * {@link RedisClient} 接口基于 Redisson 的实现。
 * <p>
 * An implementation of the {@link RedisClient} interface based on Redisson.
 *
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonRedisClient implements RedisClient {

    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    @Override
    public boolean exists(String key) {
        return redisson.getBucket(key).isExists();
    }

    @Override
    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            redisson.getKeys().delete(keys);
        }
    }

    @Override
    public void delete(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisson.getKeys().delete(keys.toArray(new String[0]));
        }
    }

    @Override
    public void expire(String key, Duration timeout) {
        if (timeout != null && !timeout.isNegative()) {
            redisson.getKeys().expire(key, timeout.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public <T> T get(String key) {
        RBucket<T> bucket = redisson.getBucket(key);
        return bucket.get();
    }

    @Override
    public Map<String, Object> mget(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) redisson.getBuckets().get(keys.toArray(new String[0]));
    }

    @Override
    public void set(String key, Object value, Duration timeout) {
        if (value == null) {
            // Redisson 的 set(null) 会导致 NPE，这里我们将其视作删除操作
            delete(key);
            return;
        }
        RBucket<Object> bucket = redisson.getBucket(key);
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            bucket.set(value, timeout);
        } else {
            bucket.set(value);
        }
    }

    @Override
    public void mset(Map<String, Object> data, Duration timeout) {
        if (data == null || data.isEmpty()) {
            return;
        }
        RBatch batch = redisson.createBatch(BatchOptions.defaults());
        data.forEach((key, value) -> {
            if (value != null) {
                if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
                    batch.getBucket(key).setAsync(value, timeout);
                } else {
                    batch.getBucket(key).setAsync(value);
                }
            }
        });
        batch.execute();
    }

    @Override
    public <T> List<T> getList(String key) {
        return redisson.getList(key);
    }

    @Override
    public void setList(String key, List<?> value, Duration timeout) {
        RList<Object> list = redisson.getList(key);
        list.delete(); // 清空旧列表
        if (value != null && !value.isEmpty()) {
            list.addAll(value);
            if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
                list.expire(timeout);
            }
        }
    }

    @Override
    public <T> Set<T> getSet(String key) {
        return redisson.getSet(key);
    }

    @Override
    public void sAdd(String key, Duration timeout, Object... members) {
        if (members == null || members.length == 0) {
            return;
        }
        RSet<Object> set = redisson.getSet(key);
        set.addAll(List.of(members));
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            set.expire(timeout);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> sunionAndFindMisses(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put(JMultiCacheConstants.UNION_RESULT, Collections.emptySet());
            result.put(JMultiCacheConstants.UNION_MISSED_KEYS, Collections.emptyList());
            return result;
        }

        // 1. 定义 Lua 脚本
        String luaScript =
                "local existingKeys = {} \n" +
                        "local missedKeys = {} \n" +
                        "for i, key in ipairs(KEYS) do \n" +
                        "  if redis.call('EXISTS', key) == 1 then \n" +
                        "    table.insert(existingKeys, key) \n" +
                        "  else \n" +
                        "    table.insert(missedKeys, key) \n" +
                        "  end \n" +
                        "end \n" +
                        "local unionResult = {} \n" +
                        "if #existingKeys > 0 then \n" +
                        "  unionResult = redis.call('SUNION', unpack(existingKeys)) \n" +
                        "end \n" +
                        "return {unionResult, missedKeys}";
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        try {

            // 2. 执行脚本
            List<Object> keysAsObjects = List.copyOf(keys);

            // eval 返回的是一个包含两个元素的列表: [并集结果列表, 未命中key列表]
            List<Object> result = script.eval(
                    RScript.Mode.READ_ONLY,
                    luaScript,
                    RScript.ReturnType.MULTI,
                    keysAsObjects
            );

            // 3. 解析返回的结果
            Map<String, Object> finalResult = new HashMap<>();
            if (result != null && result.size() == 2) {
                Set<String> unionSet = Set.copyOf((List<String>) result.get(0));
                List<String> missedList = (List<String>) result.get(1);
                finalResult.put(JMultiCacheConstants.UNION_RESULT, unionSet);
                finalResult.put(JMultiCacheConstants.UNION_MISSED_KEYS, missedList);
            } else {

                // 如果脚本执行异常或返回格式不对，做降级处理
                finalResult.put(JMultiCacheConstants.UNION_RESULT, Collections.emptySet());
                finalResult.put(JMultiCacheConstants.UNION_MISSED_KEYS, keys); // 认为全部未命中
            }
            return finalResult;

        } catch (Exception e) {
            log.error("执行 sunionAndFindMisses Lua 脚本失败. Keys: {}", keys, e);

            // 异常时，认为全部未命中
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put(JMultiCacheConstants.UNION_RESULT, Collections.emptySet());
            errorResult.put(JMultiCacheConstants.UNION_MISSED_KEYS, keys);
            return errorResult;
        }
    }

    @Override
    public <T> T hget(String key, String field) {
        RMap<String, T> map = redisson.getMap(key);
        return map.get(field);
    }

    @Override
    public <K, V> Map<K, V> hgetAll(String key) {
        return redisson.getMap(key);
    }

    @Override
    public void hset(String key, String field, Object value, Duration timeout) {
        RMap<String, Object> map = redisson.getMap(key);
        map.put(field, value);
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            map.expire(timeout);
        }
    }

    @Override
    public void hmset(String key, Map<String, Object> map, Duration timeout) {
        RMap<String, Object> rMap = redisson.getMap(key);
        rMap.putAll(map);
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            rMap.expire(timeout);
        }
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        try {
            RLock lock = redisson.getLock(lockKey);
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to acquire distributed lock due to interruption: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        try {
            if (redisson.isShutdown() || redisson.isShuttingDown()) {
                log.warn("Redisson is shutting down, skipping unlock for key: {}", lockKey);
                return;
            }
            RLock lock = redisson.getLock(lockKey);
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("An exception occurred while releasing distributed lock: {}", e.getMessage(), e);
        }
    }

    @Override
    public void publish(String channel, Object message) {
        String jsonMsg = null;
        try {
            jsonMsg = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        redisson.getTopic(channel, org.redisson.client.codec.StringCodec.INSTANCE)
                .publish(jsonMsg);
    }

    @Override
    public BatchOperation createBatchOperation() {
        RBatch batch = this.redisson.createBatch(BatchOptions.defaults());
        return new RedissonBatchOperation(batch);
    }
}
