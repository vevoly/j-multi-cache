package io.github.vevoly.jmulticache.core.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.api.message.JMultiCacheEvictMessage;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.FieldBasedStorageStrategy;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import io.github.vevoly.jmulticache.api.wrap.UnionReadResult;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.utils.I18nLogger;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheContextHandler;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheInternalHelper;
import io.github.vevoly.jmulticache.core.wrap.JMultiCacheResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link JMultiCache} 和 {@link JMultiCacheOps} 接口的核心实现类。
 * <p>
 * 此类编排了整个多级缓存的查询链路，包括 L1 (本地) 缓存、L2 (Redis) 缓存和最终的数据源加载。
 * 它通过动态注入所有 {@link RedisStorageStrategy} 实现，来支持不同数据结构的缓存。
 * <p>
 * The core implementation class for the {@link JMultiCache} and {@link JMultiCacheOps} interfaces.
 * This class orchestrates the entire multi-level cache lookup chain, including L1 (local) cache, L2 (Redis) cache, and final data source loading.
 * It supports caching of different data structures by dynamically injecting all {@link RedisStorageStrategy} implementations.
 *
 * @author vevoly
 */
@Slf4j
class JMultiCacheImpl implements JMultiCache, JMultiCacheOps {

    private final Executor asyncExecutor;
    private final RedisClient redisClient;
    private final CacheManager caffeineCacheManager;
    private final JMultiCacheConfigResolver configResolver;
    private final Map<String, RedisStorageStrategy<?>> strategyMap = new ConcurrentHashMap<>();

    private final I18nLogger i18nLog = new I18nLogger(log);
    public static final String LOG_PREFIX = "[JMultiCache] ";

    JMultiCacheImpl(
            RedisClient redisClient,
            @Qualifier("jMultiCacheCaffeineManager") CacheManager caffeineCacheManager,
            JMultiCacheConfigResolver configResolver,
            @Qualifier("jMultiCacheAsyncExecutor") Executor asyncExecutor,
            List<RedisStorageStrategy<?>> strategies
    ) {
        this.redisClient = redisClient;
        this.caffeineCacheManager = caffeineCacheManager;
        this.configResolver = configResolver;
        this.asyncExecutor = asyncExecutor;

        if (strategies != null) {
            for (RedisStorageStrategy<?> strategy : strategies) {
                this.strategyMap.put(strategy.getStorageType(), strategy);
            }
        }
    }

    // ===================================================================
    // ======== JMultiCache 接口实现 / JMultiCache Implementation =========
    // ===================================================================

    @Override
    public <T> T fetchData(String fullKey, Supplier<T> dbLoader) {
        return fetchDataUnified(fullKey, null, dbLoader);
    }

    @Override
    public <T> T fetchData(String multiCacheName, Supplier<T> dbLoader, String... keyParams) {
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        return fetchDataUnified(null, config, dbLoader, keyParams);
    }

    @Override
    public <K, V> List<V> fetchMultiDataList(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction) {
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        return fetchMultiDataUnified(config, ids, businessKey, null, queryFunction).getFlatList();
    }

    @Override
    public <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<Collection<K>, V> queryFunction) {
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        return fetchMultiDataUnified(config, ids, businessKey, null, queryFunction).getGroupedMap();
    }

    @Override
    public <K, V> Map<K, ?> fetchMultiDataMap(String multiCacheName, Collection<K> ids, String businessKey, Function<K, String> keyBuilder, Function<Collection<K>, V> queryFunction) {
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        return fetchMultiDataUnified(config, ids, businessKey, keyBuilder, queryFunction).getGroupedMap();
    }

    @Override
    public <T> Set<T> fetchUnionData(List<String> setKeysInRedis, Function<List<String>, Map<String, Set<T>>> dbQueryFunction) {
        return fetchUnionDataUnified(setKeysInRedis, dbQueryFunction);
    }

    @Override
    public <T> T fetchHashData(String hashKey, String field, Class<T> resultType, Supplier<T> queryFunction) {
        return fetchDataFromHashUnified(hashKey, field, resultType, queryFunction);
    }

    @Override
    public <V> int preloadMultiCache(String multiCacheName, Map<String, V> dataToCache) {
        return preloadCacheFromMapUnified(multiCacheName, dataToCache);
    }

    @Override
    public void evict(String multiCacheName, Object... keyParams) {
        evictUnified(multiCacheName, false, true, keyParams);
    }

    @Override
    public void evictL1(String multiCacheName, Object... keyParams) {
        evictUnified(multiCacheName, true, false, keyParams);
    }

    @Override
    public String getL1Stats(String multiCacheName) {
        try {
            // 1. 解析缓存配置 / Parse cache configuration
            ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
            if (config == null) {
                return String.format("Error: Config not found for '%s'", multiCacheName);
            }
            String namespace = config.getNamespace();

            // 2. 从 CacheManager 获取 Spring Cache / Get Spring Cache from CacheManager
            org.springframework.cache.Cache springCache = caffeineCacheManager.getCache(namespace);
            if (springCache == null) {
                return String.format("L1 Cache not initialized for namespace: %s", namespace);
            }

            // 3. 拆箱获取底层的 Caffeine Cache / Unwrap to get the underlying Caffeine Cache
            // Spring 的 Cache.getNativeCache() 返回的就是 com.github.benmanes.caffeine.cache.Cache
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>) springCache.getNativeCache();

            if (nativeCache == null) {
                return "Error: Native Caffeine cache is null.";
            }

            // 4. 获取统计快照 / Get statistics snapshot
            com.github.benmanes.caffeine.cache.stats.CacheStats stats = nativeCache.stats();

            // 5. 格式化输出 (这里返回类似于 JSON 的字符串，方便日志打印或接口返回) / Format output (here we return a string similar to JSON for easy logging or interface return)
            // requestCount = hitCount + missCount
            long requestCount = stats.requestCount();

            return String.format(
                    "Namespace: %s | " +
                            "Size: %d | " +
                            "HitRate: %.2f%% | " +
                            "Hits: %d | " +
                            "Misses: %d | " +
                            "Evictions: %d | " +
                            "LoadSuccess: %d",
                    namespace,
                    nativeCache.estimatedSize(), // 当前缓存条数估算值
                    stats.hitRate() * 100,       // 命中率百分比
                    stats.hitCount(),            // 命中次数
                    stats.missCount(),           // 未命中次数
                    stats.evictionCount(),       // 因为容量满或过期被驱逐的次数
                    stats.loadSuccessCount()     // 加载成功次数
            );

        } catch (Exception e) {
            log.error(LOG_PREFIX + "Failed to get stats for {}", multiCacheName, e);
            return "Error retrieving stats: " + e.getMessage();
        }
    }

    /**
     * [AOP专用] 根据已解析的配置对象获取单个数据。
     * 主要供 @JMultiCacheable 切面使用，避免重复解析配置。
     * 用户无需使用。
     * <p>
     * [For AOP] Fetches a single data item based on a resolved configuration object.
     * Primarily used by the @JMultiCacheable aspect to avoid redundant configuration parsing.
     * The user does not need to use it.
     *
     * @param config    已解析的缓存配置对象。/ The resolved cache configuration object.
     * @param dbLoader  数据库加载器。/ The database loader.
     * @param keyParams 用于拼接 Key 的动态参数。/ Dynamic parameters for constructing the key.
     * @param <T>       返回数据的类型。/ The type of the returned data.
     * @return 缓存或数据库中的数据。/ The data from cache or database.
     */
    public <T> T fetchDataForAop(ResolvedJMultiCacheConfig config, Supplier<T> dbLoader, String... keyParams) {
        return fetchDataUnified(null, config, dbLoader, keyParams);
    }

    /**
     * [AOP专用] 批量获取数据并返回列表。
     * 允许 AOP 传入已解析的配置和特定的 Key 构建器。
     * 用户无需使用。
     * <p>
     * [For AOP] Fetches batch data and returns a list.
     * Allows AOP to pass resolved configuration and a specific Key Builder.
     * The user does not need to use it.
     *
     * @param config        已解析的缓存配置对象。/ The resolved cache configuration object.
     * @param ids           查询 ID 集合。/ The collection of IDs to query.
     * @param businessKey   业务主键字段名。/ The business primary key field name.
     * @param keyBuilder    Key 构建函数。/ The key building function.
     * @param queryFunction 批量回源查询函数。/ The batch source query function.
     * @param <K>           ID 的类型。/ The type of the ID.
     * @param <V>           返回列表中元素的类型。/ The type of elements in the returned list.
     * @return 打平后的数据列表。/ The flattened list of data.
     */
    public <K, V> List<V> fetchMultiDataForAop(ResolvedJMultiCacheConfig config, Collection<K> ids, String businessKey, Function<K, String> keyBuilder, Function<Collection<K>, V> queryFunction) {
        return fetchMultiDataUnified(config, ids, businessKey, keyBuilder, queryFunction).getFlatList();
    }

    // =================================================================
    // ===================== 私有辅助方法和内部类 ======================
    // =================================================================

    /**
     * 根绝数据类型返回对应的读写策略
     * @param storageType
     * @return
     * @param <T>
     */
    private <T> RedisStorageStrategy<T> getStrategy(String storageType) {
        return (RedisStorageStrategy<T>) strategyMap.get(storageType.toUpperCase());
    }

    private <T> FieldBasedStorageStrategy<T> getFieldBasedStrategy(String storageType) {
        RedisStorageStrategy<?> strategy = strategyMap.get(storageType.toUpperCase());
        if (!(strategy instanceof FieldBasedStorageStrategy)) {
            throw new UnsupportedOperationException(LOG_PREFIX + "存储类型 " + storageType + " 不支持按字段操作！");
        }
        return (FieldBasedStorageStrategy<T>) strategy;
    }

    /**
     * 通用单体数据读取实现（支持从 fullKey 自动解析 config，或直接传入 config）
     *
     * @param fullKey               完整缓存 key（namespace:...）
     * @param config                可选的 MultiCacheConfig（如果为 null 则通过 fullKey 解析）
     * @param dbLoader              DB 查询函数
     * @param keyParams				key值
     * @param <T>                   返回类型
     * @return 查询结果（可能为 null）
     */
    private <T> T fetchDataUnified(
            String fullKey,
            ResolvedJMultiCacheConfig config,
            Supplier<T> dbLoader,
            String... keyParams
    ) {
        if (StringUtils.isEmpty(fullKey) && config == null) {
            log.warn(LOG_PREFIX + "MultiCacheConfig 和 fullKey 至少有一个不能为空");
            return null;
        }
        // 1. 解析或校验 config
        ResolvedJMultiCacheConfig actualConfig = config;
        if (actualConfig == null) {
            actualConfig = configResolver.resolveFromFullKey(fullKey);
        }
        if (actualConfig == null) {
            throw new IllegalArgumentException(LOG_PREFIX + "MultiCacheConfig 不能为空（无法从 key 推断）：key=" + fullKey);
        }
        // 2. 从config 中解析 key
        if (StringUtils.isEmpty(fullKey)) {
            fullKey = JMultiCacheInternalHelper.getCacheKeyFromConfig(actualConfig, keyParams);
        }
        // 3. L1 本地缓存尝试
        if (actualConfig.isUseL1()) {
            T l1Result = getFromLocalCache(actualConfig.getNamespace(), fullKey);
            if (l1Result != null) {
                return JMultiCacheInternalHelper.handleCacheHit(l1Result, config);
            }
        }
        // 4. L2 Redis 尝试
        if (actualConfig.isUseL2()) {
            TypeReference<T> typeRef = (TypeReference<T>) actualConfig.getTypeReference();
            Optional<T> l2Result = getFromRedis(fullKey, actualConfig, typeRef);
            if (l2Result.isPresent()) {
                T value = l2Result.get();
                if (actualConfig.isPopulateL1FromL2()) {
                    putInLocalCacheAsync(actualConfig, fullKey, value);
                }
                return JMultiCacheInternalHelper.handleCacheHit(value, actualConfig);
            }
        }
        // 5. DB 查询并回填
        return getFromDb(fullKey, actualConfig, dbLoader);
    }

    /**
     * 用于在框架内部传递和处理结果的封装类。
     * 它在构造时就一次性计算好两种数据视图（分组Map和打平List），供上层按需取用。
     */
    @SuppressWarnings("unchecked")
    private <K, V> JMultiCacheResult<K, V> fetchMultiDataUnified(
            ResolvedJMultiCacheConfig config,
            Collection<K> ids,
            String businessKey,
            Function<K, String> externalKeyBuilder,
            Function<Collection<K>, V> queryFunction
    ) {
        if (CollectionUtils.isEmpty(ids)) {
            return new JMultiCacheResult<>(Collections.emptyMap(), config);
        }
        if (StringUtils.isBlank(businessKey)) {
            throw new IllegalStateException(LOG_PREFIX + "无法自动推断 businessKey，手动调用请传入 businessKey 参数");
        }

        // 1. 初始化上下文： 所有复杂逻辑都被封装到这里
        JMultiCacheContextHandler<K> context = new JMultiCacheContextHandler<>(ids, businessKey, config, externalKeyBuilder, configResolver);
        // 2. 执行核心缓存逻辑
        Map<K, Object> finalResultMap = executeMultiDataUnifiedLogic(context, queryFunction);
        // 3. 返回一个包装了最终完整 Map 的 AopResult 对象
        return new JMultiCacheResult<>(finalResultMap, config);
    }


    /**
     * multiDataUnified 核心执行器
     * @param context
     * @param queryFunction
     * @return
     * @param <K>
     * @param <V>
     */
    @SuppressWarnings("unchecked")
    private <K, V> Map<K, Object> executeMultiDataUnifiedLogic(
            JMultiCacheContextHandler<K> context,
            Function<Collection<K>, V> queryFunction
    ) {
        ResolvedJMultiCacheConfig config = context.getConfig();
        Collection<K> ids = context.getIds();
        Function<K, String> keyBuilder = context.getKeyBuilder();
        String businessKey = context.getBusinessKey();

        final Map<K, Object> finalResultMap = new ConcurrentHashMap<>();
        Collection<K> missingIds = ids;

        // 1. L1 缓存
        if (config.isUseL1()) {
            missingIds = getFromLocalCacheMulti(missingIds, finalResultMap, keyBuilder, config);
            if (missingIds.isEmpty()) {
                return finalResultMap;
            }
        }
        // 2. L2 缓存
        if (config.isUseL2()) {
            missingIds = getFromRedisMulti(missingIds, finalResultMap, keyBuilder, config);
            if (missingIds.isEmpty()) {
                return finalResultMap;
            }
        }
        // 3. DB 查询
        // 这里调用 queryFunction，得到原始对象 (List or Map) 然后在 getFromDbMulti 内部利用 businessKey 转成 Map<String, V>
        Map<String, ?> dbResultAsStringKey = getFromDbMulti(new ArrayList<>(missingIds), config, businessKey, queryFunction);
        // 4. 将DB返回的 Map<String, ?> 转换回 Map<K, ?>，并合并到最终结果
        Map<String, K> businessKeyToIdMap = missingIds.stream()
                .collect(Collectors.toMap(
                        id -> JMultiCacheInternalHelper.getKeyValueSafe(id, businessKey),
                        Function.identity(), (v1, v2) -> v1
                ));
        if (MapUtils.isNotEmpty(dbResultAsStringKey)) {
            dbResultAsStringKey.forEach((businessKeyVal, value) -> {
                K originalId = businessKeyToIdMap.get(businessKeyVal);
                if (originalId != null) {
                    finalResultMap.put(originalId, value);
                } else {
                    log.warn(LOG_PREFIX + "DB返回的数据无法在missingIds中找到对应的原始ID, businessKey: {}", businessKeyVal);
                }
            });
        }
        // 5. 计算真正缺失的 ID (trulyMissingIds)
        Set<K> foundIds = finalResultMap.keySet(); // finalResultMap 包含了所有来源的数据
        List<K> trulyMissingIds = ids.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());
        // 6. 回填 L2 和 L1 缓存
        populateCacheAfterDb(config, keyBuilder, dbResultAsStringKey, businessKeyToIdMap, trulyMissingIds);

        return finalResultMap;
    }

    /**
     * 回填逻辑
     */
    private <K> void populateCacheAfterDb(
            ResolvedJMultiCacheConfig config,
            Function<K, String> keyBuilder,
            Map<String, ?> dbResultMap,
            Map<String, K> businessKeyToIdMap,
            List<K> trulyMissingIds
    ) {
        if (!config.isUseL2() && !config.isUseL1()) return;

        Map<String, Object> dataToCache = new HashMap<>();
        // 1. 准备真实数据 (Map<FullCacheKey, Value>)
        if (MapUtils.isNotEmpty(dbResultMap)) {
            dbResultMap.forEach((bizKey, value) -> {
                K originalId = businessKeyToIdMap.get(bizKey);
                if (originalId != null) {
                    String fullKey = keyBuilder.apply(originalId);
                    dataToCache.put(fullKey, value);
                }
            });
        }

        // 2. L2 回填
        if (config.isUseL2()) {
            BatchOperation batch = redisClient.createBatchOperation();
            RedisStorageStrategy<Object> strategy = getStrategy(config.getStorageType());
            if (!dataToCache.isEmpty()) {
                strategy.writeMulti(batch, dataToCache, config);
            }
            // 空值回填
            if (!trulyMissingIds.isEmpty()) {
                List<String> emptyKeys = trulyMissingIds.stream().map(keyBuilder).collect(Collectors.toList());
                strategy.writeMultiEmpty(batch, emptyKeys, config);
            }
            batch.execute();
        }
        // 3. L1 回填 (只回填真实数据)
        if (config.isUseL1() && !dataToCache.isEmpty()) {
            putInLocalCacheMultiAsync(config, dataToCache);
        }
    }

    /**
     * 获取多个 Set 的并集数据的统一处理逻辑。
     * <p>
     * 1. 尝试从 L1 获取并集缓存。
     * 2. 尝试从 L2 (Redis) 计算并集。
     * 3. 对于 L2 中缺失的 Set，回源 DB 加载。
     * 4. 将 DB 加载的数据回填到 L2 和 L1。
     * <p>
     * Unified logic for retrieving the union of multiple Sets.
     * 1. Try to get the union result from L1.
     * 2. Try to compute the union from L2 (Redis).
     * 3. Load missing Sets from DB.
     * 4. Populate L2 and L1 with data loaded from DB.
     */
    @SuppressWarnings("unchecked")
    private <T> Set<T> fetchUnionDataUnified(
            List<String> setKeysInRedis,
            Function<List<String>, Map<String, Set<T>>> dbQueryFunction
    ) {
        if (CollectionUtils.isEmpty(setKeysInRedis)) {
            return Collections.emptySet();
        }

        // 1. 解析配置 (Config)
        String primaryKey = setKeysInRedis.get(0);
        ResolvedJMultiCacheConfig config = configResolver.resolveFromFullKey(primaryKey);
        if (config == null) {
            // 如果无法解析配置，这通常是致命错误，因为我们不知道 TTL 等信息
            throw new IllegalStateException("Could not resolve config: " + primaryKey);
        }
        // 获取 Set 策略
        RedisStorageStrategy<Set<T>> strategy = getStrategy(DefaultStorageTypes.SET);
        TypeReference<Set<T>> typeRef = (TypeReference<Set<T>>) config.getTypeReference();

        Set<T> finalResult = new HashSet<>();
        List<String> missingKeysAfterL1 = new ArrayList<>(); ;

        // 1. 查询 L1
        if (config.isUseL1()) {
            for (String key : setKeysInRedis) {
                // 尝试从 L1 获取单个 Set
                Set<T> l1Set = getFromLocalCache(config.getNamespace(), key);
                if (l1Set != null) {
                    // L1 命中：处理空值占位符，然后加入最终结果
                    if (!JMultiCacheHelper.isSpecialEmptyData(l1Set, config)) {
                        finalResult.addAll(l1Set);
                    }
                    // 如果是空值占位符，什么都不做，但也算命中了（不需要查L2）
                } else {
                    // L1 未命中
                    missingKeysAfterL1.add(key);
                }
            }
        } else {
            missingKeysAfterL1 = setKeysInRedis; // 没开 L1，全给 L2
        }

        // 如果 L1 全部命中，直接返回计算结果！
        if (missingKeysAfterL1.isEmpty()) {
            return finalResult;
        }

        // 2. 查询 L2 (Redis)
        List<String> missingKeysAfterL2 = missingKeysAfterL1;
        if (config.isUseL2()) {
            try {
                // 调用接口的 readUnion，它会返回并集结果和未命中的 key
                UnionReadResult<T> l2ReadResult = strategy.readUnion(redisClient, setKeysInRedis, typeRef, config);
                if (l2ReadResult.getUnionResult() != null && !l2ReadResult.getUnionResult().isEmpty()) {
                    finalResult.addAll(l2ReadResult.getUnionResult());
                }
                missingKeysAfterL2 = l2ReadResult.getMissedKeys();
                if (missingKeysAfterL2.isEmpty()) {
                    return finalResult; // 不再回填L1
                }
                i18nLog.info("l2.union_partial_hit", l2ReadResult.getUnionResult().size(), missingKeysAfterL2.size());
            } catch (Exception e) {
                i18nLog.error("l2.union_error", e, setKeysInRedis, e.getMessage());
                missingKeysAfterL2 = missingKeysAfterL1; // L2 异常，降级：认为所有 key 都未命中，去查 DB
            }
        }
        // 3. DB 查询 (处理 L2 未命中的部分)
        i18nLog.info("db.union_load", missingKeysAfterL2);
        Map<String, Set<T>> dbResultMap;
        try {
            // 这里没有加分布式锁，因为并集操作通常涉及多个 Key，加锁粒度不好控制且容易死锁
            // 假设 dbQueryFunction 自身能处理好并发或这是允许的开销
            dbResultMap = dbQueryFunction.apply(missingKeysAfterL2);
        } catch (Exception e) {
            i18nLog.error("db.union_load_error", e, e.getMessage());
            dbResultMap = Collections.emptyMap();
        }
        // 合并 DB 结果到最终结果
        if (dbResultMap != null && !dbResultMap.isEmpty()) {
            dbResultMap.values().forEach(finalResult::addAll);
        }
        // 4. 回填 L2 或 L1
        if (config.isUseL2() || config.isUseL1()) {
            try {
                // 4.1 回填 L2 (Redis)
                if (config.isUseL2()) {
                    BatchOperation batch = redisClient.createBatchOperation();
                    if (MapUtils.isNotEmpty(dbResultMap)) {
                        strategy.writeMulti(batch, dbResultMap, config);
                    }
                    // 处理空值
                    Set<String> foundDbKeys = (dbResultMap != null) ? dbResultMap.keySet() : Collections.emptySet();
                    List<String> keysToMarkEmpty = missingKeysAfterL2.stream()
                            .filter(key -> !foundDbKeys.contains(key))
                            .collect(Collectors.toList());
                    if (!keysToMarkEmpty.isEmpty()) {
                        strategy.writeMultiEmpty(batch, keysToMarkEmpty, config);
                    }
                    batch.execute();
                }

                // 4.2 回填 L1 (本地)
                // 把从 DB 查到的每一个单独的 Set，分别塞回 L1
                if (config.isUseL1() && MapUtils.isNotEmpty(dbResultMap)) {
                    Map<String, Object> l1PopulateMap = new HashMap<>(dbResultMap);
                    putInLocalCacheMultiAsync(config, l1PopulateMap);
                }
            } catch (Exception e) {
                log.error("[JMultiCache] Populate cache failed.", e);
            }
        }
        return finalResult;
    }

    /**
     * 从hash中获取一个field
     * 这个方法缓存空值标记时会修改整个hash的过期时间，所以没有对空值进行回种，不建议使用
     * @param hashKey		key
     * @param field			item
     * @param resultType	存储类型
     * @param queryFunction	sql
     * @return
     * @param <T>
     */
    @SuppressWarnings("unchecked")
    private <T> T fetchDataFromHashUnified (String hashKey, String field, Class<T> resultType, Supplier<T> queryFunction) {
        ResolvedJMultiCacheConfig config = configResolver.resolveFromFullKey(hashKey);
        String localCacheKey = hashKey + ":" + field;

        // 1. 尝试从 L1 获取
        T l1Result = getFromLocalCache(config.getNamespace(), localCacheKey);
        if (l1Result != null) {
            return JMultiCacheHelper.isEmpty(l1Result) ? null : l1Result;
        }

        // 2. 尝试从 L2 获取
        Optional<T> l2Result = getFromRedisHash(hashKey, field, config, localCacheKey, resultType);
        if (l2Result.isPresent()) {
            return JMultiCacheInternalHelper.handleCacheHit(l2Result.get(), config);
        }

        // 3. 从数据库加载
        return getFromDbHash(hashKey, field, config, resultType, queryFunction, getFieldBasedStrategy(config.getStorageType()));
    }

    /**
     * 缓存预热
     * 从数据库加载全量数据，并批量写入到L2和L1缓存中
     *
     * @param multiCacheName   缓存名
     * @param redisKeyBuilder  一个函数，用于根据单个实体构建其在Redis中的唯一Key
     * @param queryAllFunction 一个函数，用于从数据库查询【所有】需要被缓存的实体列表
     * @return 成功预热的缓存条目数量
     * @param <K> 实体的唯一标识类型 (用于构建Map的Key)
     * @param <V> 实体本身的类型
     */
    private <K, V> int preloadCache(
            String multiCacheName,
            Function<V, String> redisKeyBuilder,
            Supplier<List<V>> queryAllFunction
    ){
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        log.info(LOG_PREFIX + "[WARM-UP] 开始为 '{}' 执行缓存预热，策略: {}", multiCacheName, config.getStoragePolicy());
        StopWatch stopWatch = new StopWatch("CacheWarmUp-" + config.getNamespace());

        try {
            // 1. 从数据库加载全量数据
            List<V> allEntities = queryAllFunction.get();
            if (CollectionUtils.isEmpty(allEntities)) {
                log.warn(LOG_PREFIX + "[WARM-UP] 从数据库未查询到任何数据，预热结束。Namespace: {}", config.getNamespace());
                return 0;
            }
            // 2. 准备批量写入的数据
            Map<String, V> dataToCacheL2 = allEntities.stream()
                    .collect(Collectors.toMap(redisKeyBuilder, Function.identity(), (a, b) -> b));
            // 3. 根据策略回填 L2 (Redis)
            if (config.isUseL2()) {
                RedisStorageStrategy<V> strategy = getStrategy(config.getStorageType());
                BatchOperation batchOperation = redisClient.createBatchOperation();
                strategy.writeMulti(batchOperation, dataToCacheL2, config);
                batchOperation.execute();
            }
            // 4. 根据策略回填 L1 (Caffeine)
            if (config.isUseL1()) {
                // L1缓存通常不应包含空值标记，所以我们只回填真实数据
                putInLocalCacheMultiAsync(config, new HashMap<>(dataToCacheL2));
            }
            log.info(LOG_PREFIX + "[WARM-UP] 缓存预热成功。Namespace: {}, 数量: {}",
                    config.getNamespace(), allEntities.size(), stopWatch.prettyPrint());
            return allEntities.size();
        } catch (Exception e) {
            log.error(LOG_PREFIX + "[WARM-UP] 缓存预热失败。Namespace: {}", config.getNamespace(), e);
            return -1; // 返回-1表示失败 / return -1 to indicate failure
        }
    }

    /**
     * 缓存预热。直接接收一个准备好的Map，并批量写入到L1和L2缓存中。
     * 使用默认的 L1_L2_DB 缓存策略。
     *
     * @param multiCacheName	要预热的缓存的名
     * @param dataToCache 		待缓存的数据，Key是Redis Key，Value是要缓存的对象
     * @return 成功预热的缓存条目数量
     * @param <V>         要缓存的对象的类型
     */
    private  <V> int preloadCacheFromMapUnified(
            String multiCacheName,
            Map<String, V> dataToCache
    ) {
        if (dataToCache == null || dataToCache.isEmpty()) {
            log.warn(LOG_PREFIX + "[WARM-UP-MAP] 传入的预热数据为空，预热结束。多级缓存名: {}", multiCacheName);
            return 0;
        }

        // 获取多级缓存配置
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        // 构建归一化后的数据 Map (Normalized Data Map)
        // 确保后续写入 L1 和 L2 的 Key 都是完整的 Full Key (Namespace:ID)
        Map<String, V> finalDataMap = new HashMap<>(dataToCache.size());
        String namespace = config.getNamespace();
        String prefixToCheck = namespace + ":";
        for (Map.Entry<String, V> entry : dataToCache.entrySet()) {
            String rawKey = entry.getKey();
            V value = entry.getValue();

            String fullKey;
            // 判断逻辑：如果 key 已经包含了 namespace 前缀，则认为是完整 key；否则，认为是 ID，进行拼接
            if (rawKey.startsWith(prefixToCheck)) {
                fullKey = rawKey;
            } else {
                fullKey = prefixToCheck + rawKey;
            }
            finalDataMap.put(fullKey, value);
        }

        log.info(LOG_PREFIX + "[WARM-UP-MAP] 开始为 '{}' 执行缓存预热，策略: {}，数量: {}",
                config.getNamespace(), config.getStoragePolicy(), finalDataMap.size());
        try {
            // 1. 根据策略回填 L2 Redis
            if (config.isUseL2()) {
                RedisStorageStrategy<V> strategy = getStrategy(config.getStorageType());
                BatchOperation batchOperation = redisClient.createBatchOperation();
                strategy.writeMulti(batchOperation, finalDataMap, config);
                batchOperation.execute();
            }
            // 2. 根据策略回填 L1 Caffeine
            if (config.isUseL1()) {
                putInLocalCacheMultiAsync(config, new HashMap<>(finalDataMap));
            }

            log.info(LOG_PREFIX + "[WARM-UP-MAP] 缓存预热成功。Namespace: {}, 数量: {}",
                    config.getNamespace(), finalDataMap.size());
            return finalDataMap.size();
        } catch (Exception e) {
            log.error(LOG_PREFIX + "[WARM-UP-MAP] 缓存预热失败。Namespace: {}", config.getNamespace(), e);
            return -1; // 返回-1表示失败 / return -1 to indicate failure
        }
    }

    /**
     * 清除一个缓存项。
     * <p>
     * Evicts a cache item.
     *
     * @param multiCacheName 缓存配置的唯一名称。/ The unique name of the cache configuration.
     * @param isOnlyL1         是否只清除 L1 本地缓存。/ Whether to only clear the L1 local cache.
     * @param keyParams      用于构建要清除的缓存键的动态参数。/ The dynamic parameters to build the final cache key to evict.
     *
     */
    private void evictUnified(String multiCacheName, boolean isOnlyL1, boolean isBroadcast, Object... keyParams) {
        // 1. 解析配置 / Resolve configuration
        ResolvedJMultiCacheConfig config = configResolver.resolve(multiCacheName);
        // 2. 构建完整的 Key / Build the full Key
        String fullKey;
        if (keyParams.length == 1 && keyParams[0] instanceof String
                && ((String) keyParams[0]).startsWith(config.getNamespace())) {
            // 🌟 智能判断：如果传入的参数已经是完整的 Key (包含 namespace 前缀)
            // 这通常是 Listener 传过来的
            fullKey = (String) keyParams[0];
        } else {
            // 常规情况：拼接 Key
            String[] stringParams = Arrays.stream(keyParams)
                    .map(String::valueOf)
                    .toArray(String[]::new);
            fullKey = JMultiCacheHelper.buildKey(config.getNamespace(), stringParams);
        }

        // 3. 清除 L2 (Redis) / Evict L2 (Redis)
        if (!isOnlyL1) {
            if (config.isUseL2()) {
                redisClient.delete(fullKey);
                i18nLog.info("evict.l2_success", fullKey);
            }
        }
        // 4. 清除 L1 (本地) / Evict L1 (Local)
        if (config.isUseL1()) {
            evictFromLocalCache(config.getNamespace(), fullKey);
            // 5. 发送集群广播清除 L1 (本地)
            if (isBroadcast) {
                try {
                    JMultiCacheEvictMessage message = new JMultiCacheEvictMessage(config.getName(), fullKey);
                    redisClient.publish(JMultiCacheConstants.J_MULTI_CACHE_EVICT_TOPIC, message);
                    log.info("evict.broadcast_sent", fullKey);
                } catch (Exception e) {
                    i18nLog.error("evict.broadcast_error", e, config.getName(), fullKey, e.getMessage());
                }

            }
        }
    }

    /**
     * 从 L1 (本地) 缓存中移除指定 Key。
     * <p>
     * Evicts a specific key from the L1 (local) cache.
     *
     * @param namespace 缓存命名空间。/ The cache namespace.
     * @param key       要移除的 Key。/ The key to evict.
     */
    private void evictFromLocalCache(String namespace, String key) {
        try {
            org.springframework.cache.Cache cache = caffeineCacheManager.getCache(namespace);
            if (cache != null) {
                cache.evict(key);
                i18nLog.info("evict.l1_success", key);
            }
        } catch (Exception e) {
            i18nLog.error("evict.l1_error", e, namespace, key, e.getMessage());
        }
    }

    /**
     * 从本地缓存 L1 获取数据
     * @param namespace	命名空间
     * @param key		键
     * @return
     * @param <T>
     */
    private <T> T getFromLocalCache(String namespace, String key) {
        try {
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache = (com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCacheManager.getCache(namespace).getNativeCache();
            if (caffeineCache == null) {
                log.warn(LOG_PREFIX + "[L1 WARN] Caffeine cache '{}' not found.", namespace);
                return null;
            }
            Object result = caffeineCache.getIfPresent(key);
            if (result != null) {
                log.info(LOG_PREFIX + "[L1 HIT] Key: {}", key);
                return (T) result;
            } else {
                log.info(LOG_PREFIX + "[L1 MISS] Key: {}", key);
                return null;
            }
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "[L1_GET ERROR] Namespace: {}, Key: {}", namespace, key, e);
            return null;
        }
    }

    /**
     * 从本地缓存 L1 批量获取数据。
     *
     * @param ids           需要查询的ID列表
     * @param resultMap     用于存放命中结果的Map
     * @param keyBuilder    Key构造函数
     * @param config     	解析过的配置
     * @return 未在L1中命中的ID列表
     */
    private <K, V> Collection<K> getFromLocalCacheMulti(
            Collection<K> ids, Map<K, V> resultMap,
            Function<K, String> keyBuilder,
            ResolvedJMultiCacheConfig config
    ) {
        List<K> missingFromL1 = new ArrayList<>();
        Cache<String, Object> caffeineCache = (Cache<String, Object>) caffeineCacheManager.getCache(config.getNamespace()).getNativeCache();

        for (K id : ids) {
            String key = keyBuilder.apply(id);
            Object cachedValue = (caffeineCache != null) ? caffeineCache.getIfPresent(key) : null;
            if (cachedValue != null) {
                V entity = JMultiCacheInternalHelper.handleCacheHit((V) cachedValue, config);
                if (entity != null) {
                    resultMap.put(id, entity);
                }
            } else {
                missingFromL1.add(id);
            }
        }
        log.info(LOG_PREFIX + "[L1 MULTI] namespace: {} Hit: {}, Miss: {}", config.getNamespace(), resultMap.size(), missingFromL1.size());
        return missingFromL1;
    }

    /**
     * 从 redis 中 获取数据
     * @param key		redis key
     * @param config	缓存配置
     * @param typeRef	类型
     * @return
     * @param <T>
     */
    private <T> Optional<T> getFromRedis(
            String key,
            ResolvedJMultiCacheConfig config,
            TypeReference<T> typeRef
    ) {
        RedisStorageStrategy<T> strategy = getStrategy(config.getStorageType());
        T result = strategy.read(redisClient, key, typeRef, config);
        if (result != null) {
            log.info(LOG_PREFIX + "[L2 HIT] Key: {}", key);
            return Optional.ofNullable(result); // 不在这里回填L1
        }
        log.info(LOG_PREFIX + "[L2 MISS] Key: {}", key);
        return Optional.empty();
    }

    private <T> Optional<T> getFromRedisHash(
            String hashKey,
            String field,
            ResolvedJMultiCacheConfig config,
            String localCacheKey,
            Class<T> resultType
    ) {
        FieldBasedStorageStrategy<T> strategy = getFieldBasedStrategy(config.getStorageType());
        T result = strategy.readField(redisClient, hashKey, field, resultType, config);
        if (result != null) {
            log.info(LOG_PREFIX + "[L2-HASH HIT] Key: {}, Field: {}", hashKey, field);
            putInLocalCacheAsync(config, localCacheKey, result);
            return Optional.ofNullable(result);
        }
        log.info(LOG_PREFIX + "[L2-HASH MISS] Key: {}, Field: {}", hashKey, field);
        return Optional.empty();
    }

    /**
     * 从 Redis L2 批量获取数据。
     *
     * @param missingFromL1 L1未命中的ID列表
     * @param resultMap     用于存放命中结果的Map
     * @param keyBuilder    Key构造函数
     * @param config        缓存配置
     * @return 未在L2中命中的ID列表
     */
    private <K, V> Collection<K> getFromRedisMulti(
            Collection<K> missingFromL1,
            Map<K, V> resultMap,
            Function<K, String> keyBuilder,
            ResolvedJMultiCacheConfig config
    ) {
        if (missingFromL1.isEmpty()) {
            return Collections.emptyList();
        }

        RedisStorageStrategy<?> strategy = getStrategy(config.getStorageType());
        Map<String, K> keyToIdMap = missingFromL1.stream()
                .collect(Collectors.toMap(keyBuilder, Function.identity(), (a, b) -> a));
        List<String> keysToRead = new ArrayList<>(keyToIdMap.keySet());
        BatchOperation batchOperation = redisClient.createBatchOperation();
        TypeReference<V> typeRef = (TypeReference<V>) config.getTypeReference();
        // 从策略获取包含了“转换后”数据的Future Map
        Map<String, CompletableFuture<Optional<V>>> futureMap = strategy.readMulti(batchOperation, keysToRead, typeRef, config);
        batchOperation.execute();

        List<K> missingFromL2 = new ArrayList<>();
        // 遍历最终的Future，获取结果
        for (String key : keysToRead) {
            K id = keyToIdMap.get(key);
            CompletableFuture<Optional<V>> future = futureMap.get(key);
            try {
                // .join() 获取的就是已经由策略转换好的、最终类型为V的实体
                Optional<V> optionalEntity = future.join();
                if (optionalEntity == null) {
                    missingFromL2.add(id); // L2 缓存未命中
                } else {
                    // L2 缓存命中, 只要 optionalEntity 不为 null，就说明 Redis 中有记录
                    if (optionalEntity.isPresent()) {
                        V entity = optionalEntity.get(); // 命中，且有真实数据
                        resultMap.put(id, entity);
                        // 根据策略决定是否回填L1
                        if (config.isPopulateL1FromL2()) {
                            putInLocalCacheAsync(config, key, entity);
                        }
                    }
                    // 命中，但是空标记。不将其放入 resultMap，也不写入 L1 本地缓存。
                }
            } catch (Exception e) {
                log.error(LOG_PREFIX + "[L2 MULTI] FUTURE GET FAILED Key: {}. Error: {}", key, e.getMessage());
                missingFromL2.add(id);
            }
        }
        log.info(LOG_PREFIX + "[L2 MULTI] Hit: {}, Miss: {}", missingFromL1.size() - missingFromL2.size(), missingFromL2.size());
        return missingFromL2;
    }

    /**
     * 从最终数据源 (DB) 加载数据，并执行缓存回填。
     * <p>
     * 此方法包含了分布式锁逻辑，以防止高并发场景下的“缓存击穿”问题。
     * 同时实现了“双重检查锁定”模式，避免多个线程获取锁后重复查询数据库。
     * <p>
     * Loads data from the source of truth (DB) and performs cache population.
     * This method includes distributed locking logic to prevent "cache breakdown" in high-concurrency scenarios.
     * It also implements the "Double-Checked Locking" pattern to avoid repeated database queries by multiple threads acquiring the lock sequentially.
     *
     * @param key      完整的缓存键。/ The full cache key.
     * @param config   当前操作的已解析配置。/ The resolved configuration for the current operation.
     * @param dbLoader 用于从数据源加载数据的 Supplier (替代了旧的 CacheQueryFunction)。/ A supplier to load data from the data source (replaces the old CacheQueryFunction).
     * @param <T>      数据的泛型类型。/ The generic type of the data.
     * @return 从数据源加载的数据。/ The data loaded from the data source.
     */
    private <T> T getFromDb (
            String key,
            ResolvedJMultiCacheConfig config,
            Supplier<T> dbLoader
    ) {
        // 1. 构建分布式锁的 Key
        String lockKey = "jmc:lock:" + key;
        // 2. 尝试获取分布式锁 (等待 5s, 持有 10s)
        if (redisClient.tryLock(lockKey, 5, 10, TimeUnit.SECONDS)) {
            try {
                log.info(LOG_PREFIX + "[LEADER] 获取锁成功, 查询数据库 key: {}", key);
                // 3. 双重检查锁定 (Double-Check)
                // 在获取锁之后，再次检查 L2 缓存。因为在当前线程等待锁的过程中，可能有前一个持有锁的线程已经完成了 DB 查询并回填了缓存。
                if (config.isUseL2()) {
                    // 从 config 中获取预解析的 TypeReference
                    TypeReference<T> typeRef = (TypeReference<T>) config.getTypeReference();
                    Optional<T> recheckResult = getFromRedis(key, config, typeRef);
                    if (recheckResult.isPresent()) {
                        i18nLog.info("db.hit_l2_after_lock", key);
                        T value = recheckResult.get();
                        // 如果配置了回填 L1，这里也需要补上，因为其他线程只回填了 L2
                        if (config.isPopulateL1FromL2()) {
                            putInLocalCacheAsync(config, key, value);
                        }
                        // 处理可能存在的空值标记
                        return (T) JMultiCacheInternalHelper.handleCacheHit(value, config);
                    }
                }

                // 4. 执行数据库查询
                T dbResult = dbLoader.get();
                Object valueToCache;

                // 5. 处理空值 (防止缓存穿透)
                if (JMultiCacheHelper.isResultEmptyFromDb(dbResult)) {
                    TypeReference<Object> typeRef = (TypeReference<Object>) config.getTypeReference();
                    valueToCache = JMultiCacheInternalHelper.createEmptyData(typeRef, config); // 如果 DB 返回空，生成一个特殊的空值标记对象
                } else {
                    valueToCache = dbResult;
                }
                // 6. 回填 L2 (Redis) 缓存
                if (config.isUseL2()) {
                    // 动态获取策略
                    RedisStorageStrategy<Object> strategy = getStrategy(config.getStorageType());
                    // 写入 (config 中包含了 TTL 和 emptyValueMark 信息，策略内部会处理)
                    strategy.write(redisClient, key, valueToCache, config);
                    log.info("l2.populate_success", key);
                }
                // 7. 回填 L1 (本地) 缓存
                if (config.isUseL1()) {
                    putInLocalCacheAsync(config, key, valueToCache);
                }
                return dbResult;
            } finally {
                // 8. 释放锁
                redisClient.unlock(lockKey);
            }
        } else {
            log.warn(LOG_PREFIX + "[FOLLOWER] 获取锁失败, 等待后重新尝试... Key: {}", key);
            // 获取锁失败，说明有 Leader 线程正在查库。短暂休眠后，重试读取缓存 (而不是去查库，防止击穿)
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 重试逻辑：根据策略尝试读取 L2 或 L1
            if (config.isUseL2()) {
                TypeReference<T> typeRef = (TypeReference<T>) config.getTypeReference();
                return getFromRedis(key, config, typeRef)
                        .map(value -> (T) JMultiCacheInternalHelper.handleCacheHit(value, config))
                        .orElse(null);
            } else if (config.isUseL1()) {
                // 如果只开启了 L1，尝试读 L1
                T l1Result = getFromLocalCache(config.getNamespace(), key);
                return (T) JMultiCacheInternalHelper.handleCacheHit(l1Result, config);
            }
            return null;
        }
    }

    /**
     * 从数据源加载 Hash Field 的数据，并回填缓存。
     * <p>
     * 注意：对于 Hash 结构，我们只回填真实数据，不回填空值标记，因为 Redis Hash 不支持对 Field 单独设置 TTL。
     * <p>
     * Loads Hash Field data from the data source and populates the cache.
     * Note: For Hash structures, we only populate real data, not null value markers, because Redis Hash does not support setting TTL for individual fields.
     *
     * @param hashKey      Hash 的主键。/ The main key of the Hash.
     * @param field        Hash 的字段名。/ The field name of the Hash.
     * @param config       当前操作的已解析配置。/ The resolved configuration for the current operation.
     * @param resultType   期望的结果类型 Class。/ The expected result Class type.
     * @param queryFunction 数据加载函数。/ The data loading function.
     * @param strategy     Hash 存储策略。/ The Hash storage strategy.
     * @param <T>          数据的泛型类型。/ The generic type of the data.
     * @return 从数据源加载的数据。/ The data loaded from the data source.
     */
    private <T> T getFromDbHash(
            String hashKey,
            String field,
            ResolvedJMultiCacheConfig config,
            Class<T> resultType,
            Supplier<T> queryFunction,
            FieldBasedStorageStrategy<T> strategy
    ) {
        String lockKey = "jmc:lock:" + hashKey + ":" + field;
        String localCacheKey = hashKey + ":" + field;

        if (redisClient.tryLock(lockKey, 5, 10, TimeUnit.SECONDS)) {
            try {
                i18nLog.info("db.hash_load_leader", hashKey, field);

                // 1. 双重检查 (Double-Check)
                // 尝试从 Redis 读取，看是否已有其他线程回填
                T cachedValue = strategy.readField(redisClient, hashKey, field, resultType, config);
                if (cachedValue != null) {
                    // 回填 L1
                    putInLocalCacheAsync(config, localCacheKey, cachedValue);
                    return cachedValue;
                }
                // 2. 查询 DB
                T result = queryFunction.get();
                // 3. 处理结果
                if (JMultiCacheHelper.isResultEmptyFromDb(result)) {
                    // Hash Field 不缓存空值
                    return null;
                }
                // 4. 回填 L2 (Redis)
                // 写入真实数据，并使用配置的 redisTtl 刷新整个 Hash 的过期时间
                strategy.writeField(redisClient, hashKey, field, result, config);
                i18nLog.info("l2.hash_populate_success", hashKey, field);
                // 5. 回填 L1 (本地)
                putInLocalCacheAsync(config, localCacheKey, result);
                return result;
            } finally {
                redisClient.unlock(lockKey);
            }
        } else {
            // Follower 逻辑：等待后重试读取 L2
            i18nLog.warn("db.hash_load_follower", hashKey, field);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 再次尝试从 L2 读取
            return strategy.readField(redisClient, hashKey, field, resultType, config);
        }
    }

    /**
     * 从数据库获取批量数据
     * @param missingIds
     * @param config
     * @param businessKey
     * @param queryFunction
     * @return
     * @param <K>
     * @param <V>
     */
    @SuppressWarnings("unchecked")
    private <K, V> Map<String, ?> getFromDbMulti(
            List<K> missingIds,
            ResolvedJMultiCacheConfig config,
            String businessKey,
            Function<Collection<K>, V>  queryFunction) {
        if (missingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String namespace = config.getNamespace();
        String lockKey = "lock:multicache:multi:" + namespace + ":" + JMultiCacheInternalHelper.getMd5Key(missingIds);

        if (redisClient.tryLock(lockKey, 10, 20, TimeUnit.SECONDS)) {
            log.info(LOG_PREFIX + "[LEADER-Multi] 获取锁成功, 查询数据库 namespace: {}", namespace);
            try {
                // 1. 查询 DB (返回 List 或 Map)
                Object dbRaw = queryFunction.apply(missingIds);
                // 2. 利用 businessKey 进行归一化，统一转为 Map<String, V>
                return JMultiCacheInternalHelper.normalizeDbResultToMap(dbRaw, businessKey);
            } finally {
                redisClient.unlock(lockKey);
            }

        } else {
            log.warn(LOG_PREFIX + "[FOLLOWER-Multi] 获取锁失败, 等待重试... namespace: {}", namespace);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyMap();
        }
    }

    /**
     * 写入本地缓存
     * @param config
     * @param key
     * @param value
     */
    private void putInLocalCacheAsync(ResolvedJMultiCacheConfig config, String key, Object value) {
        // 如果是空数据标记不回种L1
        if (JMultiCacheHelper.isSpecialEmptyData(value, config)) {
            log.info(LOG_PREFIX + "[L1 EMPTY VALUE] 无需回种 Key: {}", key);
            return;
        }
        try {
            String finalKey = key;
            CompletableFuture.runAsync(() -> {
                com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache =
                        (com.github.benmanes.caffeine.cache.Cache<String, Object>) caffeineCacheManager.getCache(config.getNamespace()).getNativeCache();
                if (caffeineCache != null) {
                    caffeineCache.put(finalKey, value);
                    log.info(LOG_PREFIX + "[L1 POPULATE] Key: {}", finalKey);
                }
            }, asyncExecutor);
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "[L1_GET ERROR] Namespace: {}, Key: {}", config.getNamespace(), key, e);
        }
    }

    /**
     * 写入本地缓存
     * @param config
     * @param dataToCache
     */
    private void putInLocalCacheMultiAsync(ResolvedJMultiCacheConfig config, Map<String, Object> dataToCache) {
        if (dataToCache == null || dataToCache.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache =
                        (Cache<String, Object>) caffeineCacheManager.getCache(config.getNamespace()).getNativeCache();
                if (caffeineCache == null) {
                    log.warn(LOG_PREFIX + "[L1-MULTI POPULATE] 本地缓存 '{}' 未找到, 跳过回种L1.", config.getNamespace());
                    return;
                }

                // 检查key，如果时老的缓存key要去掉后缀
                Map<String, Object> checkedDataToCache = new HashMap<>(dataToCache.size());

                for (Map.Entry<String, Object> entry : dataToCache.entrySet()) {
                    String originalKey = entry.getKey();
                    Object value = entry.getValue();

                    // 如果是空值标记，则不回填L1
                    if (JMultiCacheHelper.isSpecialEmptyData(value, config)) {
                        log.info(LOG_PREFIX + "[L1-MULTI EMPTY VALUE] 无需回种 Key: {}", originalKey);
                        continue;
                    }

                    // 对每个Key执行检查和转换
                    checkedDataToCache.put(originalKey, value);
                }
                // 使用转换后的Map进行批量写入
                if (!checkedDataToCache.isEmpty()) {
                    caffeineCache.putAll(checkedDataToCache);
                    log.info(LOG_PREFIX + "[L1-MULTI POPULATE] 回种数据 {} 个items 到 namespace '{}'.",
                            checkedDataToCache.size(), config.getNamespace());
                }

            } catch (Exception e) {
                log.error(LOG_PREFIX + "[L1-MULTI POPULATE ERROR] Namespace: {}", config.getNamespace(), e);
            }
        }, asyncExecutor);
	}
}
