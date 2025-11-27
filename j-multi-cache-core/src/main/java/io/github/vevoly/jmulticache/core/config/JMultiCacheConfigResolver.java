package io.github.vevoly.jmulticache.core.config;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.JMultiCacheConfigName;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStoragePolicies;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheProperties;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheRootProperties;
import io.github.vevoly.jmulticache.core.utils.I18nLogger;
import io.github.vevoly.jmulticache.core.utils.JavaTypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多级缓存配置解析器。
 * <p>
 * 此组件在 Spring 容器启动时运行 (通过 {@link InitializingBean})，负责将 YML 文件中所有的缓存配置
 * （通过 {@link JMultiCacheRootProperties} 注入）与框架默认值进行合并，
 * 构建成一系列标准化的、不可变的 {@link ResolvedJMultiCacheConfig} 对象。
 * 它是框架内部获取最终生效配置的唯一入口。
 * <p>
 * The multi-cache configuration resolver.
 * This component runs on Spring container startup (via {@link InitializingBean}), and is responsible for merging all cache configurations from YML files
 * (injected via {@link JMultiCacheRootProperties}) with framework defaults.
 * It builds a series of standardized, immutable {@link ResolvedJMultiCacheConfig} objects,
 * serving as the single entry point for the framework to obtain final, effective configurations.
 *
 * @author vevoly
 */
@Slf4j
public class JMultiCacheConfigResolver implements InitializingBean {

    private final ObjectMapper objectMapper;
    private final JMultiCacheRootProperties rootProperties;
    private Map<String, ResolvedJMultiCacheConfig> resolvedConfigMap;
    // 按 namespace 长度降序排序的列表，用于高效查找
    private List<ResolvedJMultiCacheConfig> configsSortedByNamespaceDesc;

    private final I18nLogger i18nLog = new I18nLogger(log);
    public static final String LOG_PREFIX = "[JMultiCacheResolver] ";

    public JMultiCacheConfigResolver(JMultiCacheRootProperties rootProperties, ObjectMapper objectMapper) {
        this.rootProperties = rootProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 在所有 Spring Bean 属性被设置后，由 Spring 容器自动调用。
     * <p>
     * Invoked by the Spring container after all bean properties have been set.
     */
    @Override
    public void afterPropertiesSet() {
        i18nLog.info("resolver.start_parse");
        Map<String, ResolvedJMultiCacheConfig> tempMap = new HashMap<>();

        // 1. 获取全局默认配置 / Get global default configuration
        JMultiCacheProperties defaults = Optional.ofNullable(rootProperties.getDefaults()).orElse(new JMultiCacheProperties());

        Map<String, JMultiCacheProperties> configs = rootProperties.getConfigs();
        if (configs == null || configs.isEmpty()) {
            log.warn(LOG_PREFIX + "No 'j-multi-cache.configs' block found in YML, no caches will be loaded!");
            this.resolvedConfigMap = Collections.unmodifiableMap(tempMap);
            this.configsSortedByNamespaceDesc = Collections.emptyList();
            return;
        }

        // 2. 遍历并解析每个独立的缓存配置 / Iterate and parse each individual cache configuration
        for (Map.Entry<String, JMultiCacheProperties> entry : configs.entrySet()) {
            String configName = entry.getKey();
            JMultiCacheProperties props = entry.getValue();

            try {
                // 校验 Namespace 和 EntityClass / Validate Namespace and EntityClass
                String namespace = Optional.ofNullable(props.getNamespace()).orElse(defaults.getNamespace());
                if (!StringUtils.hasText(namespace)) {
                    throw new IllegalStateException("Property 'namespace' is required.");
                }
                if (!StringUtils.hasText(props.getEntityClass())) {
                    throw new IllegalStateException("Property 'entity-class' is required.");
                }
                // 加载并预解析类型 / Load and pre-parse types
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                Class<?> entityClass = Class.forName(props.getEntityClass(), true, contextClassLoader);

                // =========================================================
                // 1. 处理可为空的字段 (TTL) - 允许为 null 以表示禁用
                // =========================================================

                // 逻辑：必须不是 0，且（如果是负数，必须是 -1）
                java.util.function.Predicate<Duration> isEnabled = d -> {
                    if (d.isZero()) return false; // 0 = 禁用
                    if (d.isNegative()) {
                        // 如果是负数，只有 -1 (毫秒或秒) 代表永不过期，允许通过
                        // 其他负数视为无效/禁用
                        // 兼容 -1 (默认为毫秒) 和 -1s (秒)
                        return d.toMillis() == -1 || d.getSeconds() == -1;
                    }
                    return true; // 正数 = 正常时间
                };
                // Redis TTL
                Duration finalRedisTtl = Optional.ofNullable(props.getRedisTtl())
                        .or(() -> Optional.ofNullable(defaults.getRedisTtl()))
                        .filter(isEnabled)
                        .orElse(null);     // 如果被过滤掉（是0或无效负数），结果为 null

                // Local TTL
                Duration finalLocalTtl = Optional.ofNullable(props.getLocalTtl())
                        .or(() -> Optional.ofNullable(defaults.getLocalTtl()))
                        .filter(isEnabled)
                        .orElse(null);     // 如果被过滤掉，结果为 null

                // =========================================================
                // 2. 处理必填字段 - 必须兜底到常量
                // =========================================================

                // Local Max Size: Config -> Default -> Constant
                Long finalLocalMaxSize = Optional.ofNullable(props.getLocalMaxSize())
                        .or(() -> Optional.ofNullable(defaults.getLocalMaxSize()))
                        .orElse(JMultiCacheConstants.DEFAULT_LOCAL_CACHE_MAX_SIZE);
                // Storage Type: Config -> Default -> Constant
                String finalStorageType = Optional.ofNullable(props.getStorageType())
                        .or(() -> Optional.ofNullable(defaults.getStorageType()))
                        .orElse(JMultiCacheConstants.DEFAULT_STORAGE_TYPE);
                // Key Field: Config -> Default -> Constant
                String finalKeyField = Optional.ofNullable(props.getKeyField())
                        .or(() -> Optional.ofNullable(defaults.getKeyField()))
                        .orElse(JMultiCacheConstants.DEFAULT_KEY_FIELD);
                // Empty Cache TTL: Config -> Default -> Constant
                Duration finalEmptyCacheTtl = Optional.ofNullable(props.getEmptyCacheTtl())
                        .or(() -> Optional.ofNullable(defaults.getEmptyCacheTtl()))
                        .orElse(Duration.ofSeconds(JMultiCacheConstants.EMPTY_CACHE_REDIS_TTL));
                // Empty Value Mark: Config -> Default -> Constant
                String finalEmptyValueMark = Optional.ofNullable(props.getEmptyCacheValue())
                        .or(() -> Optional.ofNullable(defaults.getEmptyCacheValue()))
                        .orElse(JMultiCacheConstants.EMPTY_CACHE_VALUE);
                // Business Key: Config -> Default -> Constant ("")
                String finalBusinessKey = Optional.ofNullable(props.getBusinessKey())
                        .or(() -> Optional.ofNullable(defaults.getBusinessKey()))
                        .orElse("");

                // =========================================================
                // 3. 处理依赖字段 (Storage Policy)
                // =========================================================

                // 优先用配置的 Policy，如果没有，则根据计算好的最终 TTL 进行推断
                String finalStoragePolicy = Optional.ofNullable(props.getStoragePolicy())
                        .or(() -> Optional.ofNullable(defaults.getStoragePolicy()))
                        .orElseGet(() -> inferStoragePolicyFromTtl(props, defaults));

                // =========================================================
                // 4. 构建对象
                // =========================================================
                TypeReference<?> typeReference = JavaTypeReference.from(entityClass, finalStorageType, objectMapper.getTypeFactory());
                ResolvedJMultiCacheConfig resolved = ResolvedJMultiCacheConfig.builder()
                        .name(configName)
                        .entityClass(entityClass)
                        .typeReference(typeReference)
                        .namespace(namespace)
                        .redisTtl(finalRedisTtl)
                        .localTtl(finalLocalTtl)
                        .localMaxSize(finalLocalMaxSize)
                        .storageType(finalStorageType)
                        .storagePolicy(finalStoragePolicy)
                        .keyField(finalKeyField)
                        .businessKey(finalBusinessKey)
                        .emptyCacheTtl(finalEmptyCacheTtl)
                        .emptyValueMark(finalEmptyValueMark)
                        .build();
                tempMap.put(configName, resolved);
            } catch (ClassNotFoundException e) {
                log.error(LOG_PREFIX + "YML Configuration Error: Could not find entity-class '{}' for cache '{}'. Ensure the class is in the application's classpath.", props.getEntityClass(), configName);
            } catch (Exception e) {
                log.error(LOG_PREFIX + "An unexpected error occurred while parsing configuration for '{}'.", configName, e);
            }
        }

        this.resolvedConfigMap = Collections.unmodifiableMap(tempMap);
        this.configsSortedByNamespaceDesc = this.resolvedConfigMap.values().stream()
                .sorted(Comparator.comparingInt((ResolvedJMultiCacheConfig config) -> config.getNamespace().length()).reversed())
                .collect(Collectors.toList());
        log.info(LOG_PREFIX + "Successfully loaded {} cache configurations.", this.resolvedConfigMap.size());
    }

    /**
     * 根据配置名称获取已解析的、最终生效的缓存配置。
     * <p>
     * Gets the resolved, final, and effective cache configuration by its name.
     *
     * @param configName 缓存的唯一名称 (YML 中的 Key)。/ The unique name of the cache (the key in YML).
     * @return 归一化后的配置对象。/ The normalized configuration object.
     * @throws IllegalStateException 如果在 YML 中找不到对应的配置。/ if no corresponding configuration is found in YML.
     */
    public ResolvedJMultiCacheConfig resolve(String configName) {
        if (configName == null) {
            throw new IllegalArgumentException(LOG_PREFIX + "Configuration name cannot be null.");
        }
        ResolvedJMultiCacheConfig resolved = resolvedConfigMap.get(configName);
        if (resolved == null) {
            throw new IllegalStateException(LOG_PREFIX + "No configuration found for name '" + configName + "'. Please check your YML files.");
        }
        return resolved;
    }

    /**
     * 根据实现了 {@link JMultiCacheConfigName} 接口的枚举常量获取配置。
     * <p>
     * 这是在代码中进行类型安全引用的推荐方式。
     * <p>
     * Gets the configuration based on an enum constant that implements the {@link JMultiCacheConfigName} interface.
     * This is the recommended way for type-safe referencing in code.
     *
     * @param configEnum 实现了 {@link JMultiCacheConfigName} 的枚举常量。/ An enum constant implementing {@link JMultiCacheConfigName}.
     * @return 归一化后的配置对象。/ The normalized configuration object.
     */
    public ResolvedJMultiCacheConfig resolve(JMultiCacheConfigName configEnum) {
        if (configEnum == null) {
            throw new IllegalArgumentException(LOG_PREFIX + "Configuration enum cannot be null.");
        }
        return resolve(configEnum.name());
    }

    /**
     * 根据完整的缓存 key (例如 "namespace:keyPart") 反向解析出对应的配置。
     * <p>
     * 它会返回与 key 前缀最长匹配的那个配置。
     * <p>
     * Resolves the corresponding configuration from a full cache key (e.g., "namespace:keyPart").
     * It returns the configuration with the longest matching namespace prefix.
     *
     * @param fullKey 完整的缓存 key。/ The full cache key.
     * @return 匹配到的 {@link ResolvedJMultiCacheConfig}；如果找不到则返回 {@code null}。/ The matched {@link ResolvedJMultiCacheConfig}, or {@code null} if no match is found.
     */
    public ResolvedJMultiCacheConfig resolveFromFullKey(String fullKey) {
        if (!StringUtils.hasText(fullKey)) {
            return null;
        }
        for (ResolvedJMultiCacheConfig config : configsSortedByNamespaceDesc) {
            if (fullKey.startsWith(config.getNamespace())) {
                return config;
            }
        }
        log.warn(LOG_PREFIX + "Could not infer any known cache configuration from fullKey '{}'.", fullKey);
        return null;
    }

    /**
     * 返回所有已解析的缓存配置的集合。
     * <p>
     * 主要用于像 {@code JMultiCacheCaffeineManagerAutoConfiguration} 这样的启动时配置类，用于动态注册所有 Caffeine 实例。
     * <p>
     * Returns a collection of all resolved cache configurations.
     * This is mainly used by startup-time configuration classes like {@code JMultiCacheCaffeineManagerAutoConfiguration} to dynamically register all Caffeine instances.
     *
     * @return 所有 {@link ResolvedJMultiCacheConfig} 的不可变集合。/ An unmodifiable collection of all {@link ResolvedJMultiCacheConfig}.
     */
    public Collection<ResolvedJMultiCacheConfig> getAllResolvedConfigs() {
        if (resolvedConfigMap == null) {
            return Collections.emptyList();
        }
        return resolvedConfigMap.values();
    }

    /**
     *  根据 TTL 配置，自动推断出缓存的存储与回源策略。
     * <p>
     * 框架会根据 redis-ttl 和 local-ttl 的有无来智能决定缓存链路。
     * <p>
     * Infers the cache storage and source-of-truth strategy based on TTL configurations.
     * The framework intelligently determines the cache chain based on the presence of redis-ttl and local-ttl.
     *
     * @param props    当前缓存的特定配置。/ The specific properties for the current cache.
     * @param defaults 全局默认配置。/ The global default properties.
     * @return 推断出的策略字符串，例如 "L1_L2_DB"。/ The inferred policy string, e.g., "L1_L2_DB".
     */
    private String inferStoragePolicyFromTtl(JMultiCacheProperties props, JMultiCacheProperties defaults) {
        // 合并 props 和 defaults 来获取最终的 TTL 配置 / Merge props and defaults to get the final TTL configuration
        Duration redisTtl = Optional.ofNullable(props.getRedisTtl()).orElse(defaults.getRedisTtl());
        Duration localTtl = Optional.ofNullable(props.getLocalTtl()).orElse(defaults.getLocalTtl());

        boolean useL2 = redisTtl != null && !redisTtl.isNegative() && !redisTtl.isZero();
        boolean useL1 = localTtl != null && !localTtl.isNegative() && !localTtl.isZero();

        if (useL1 && useL2) {
            return DefaultStoragePolicies.L1_L2_DB;
        }
        if (useL2) {
            return DefaultStoragePolicies.L2_DB;
        }
        if (useL1) {
            return DefaultStoragePolicies.L1_DB;
        }
        // 如果 L1 和 L2 都不使用，则只从数据库读取 / If neither L1 nor L2 is used, read only from the database
        return DefaultStoragePolicies.DB_ONLY;
    }
}
