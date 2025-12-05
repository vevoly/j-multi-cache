package io.github.vevoly.jmulticache.core.internal;

import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 降级实现类（当未启用框架时使用）。
 * 所有操作直接回源（执行 dbLoader），不经过 Redis 或 Caffeine。
 * @author vevoly
 */
@Slf4j
public class NoOpJMultiCacheManager implements JMultiCache, JMultiCacheOps {

    private static final String LOG_PREFIX = "[JMultiCache-NoOp] ";

    public NoOpJMultiCacheManager() {
        log.warn(LOG_PREFIX + "框架未启用 (@EnableJMultiCache 缺失)，缓存功能已降级为直连数据库模式。");
    }


    @Override
    public <T> T fetchData(String fullKey, Supplier<T> dbLoader) {
        return dbLoader != null ? dbLoader.get() : null;
    }

    @Override
    public <T> T fetchData(String multiCacheName, Supplier<T> dbLoader, String... keyParams) {
        return dbLoader != null ? dbLoader.get() : null;
    }

    @Override
    public <K, V> List<V> fetchMultiDataList(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction) {
        return queryFunction != null ? (List<V>) queryFunction.apply(ids) : Collections.emptyList();
    }

    @Override
    public <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction) {
        Object result = queryFunction.apply(ids);
        if (result instanceof Map) {
            return (Map<K, ?>) result;
        }
        return Collections.emptyMap();
    }

    @Override
    public <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<K, String> keyBuilder, Function<Collection<K>, V> queryFunction) {
        return null;
    }

    @Override
    public <T> Set<T> fetchUnionData(List<String> setKeysInRedis, Function<List<String>, Map<String, Set<T>>> dbQueryFunction) {
        return null;
    }

    @Override
    public <T> T fetchHashData(String hashKey, String field, Class<T> resultType, Supplier<T> queryFunction) {
        return null;
    }

    @Override
    public String computeKey(String multiCacheName, Object... keyParams) {
        return null;
    }

    @Override
    public <V> int preloadMultiCache(String multiCacheName, Map<String, V> dataToCache) {
        return 0;
    }

    @Override
    public void evict(String multiCacheName, Object... keyParams) {

    }

    @Override
    public void evictL1(String multiCacheName, Object... keyParams) {

    }

    @Override
    public String getL1Stats(String multiCacheName) {
        return null;
    }

}
