package io.github.vevoly.jmulticache.core.processor;

import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import io.github.vevoly.jmulticache.api.annotation.JMultiCachePreloadable;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheInternalHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 缓存预热执行器。
 * <p>
 * 负责在应用启动后（或特定时机），扫描并执行被 {@link JMultiCachePreloadable} 注解标记的服务，
 * 将其数据提前加载到缓存中。
 * <p>
 * Cache preload executor.
 * Responsible for scanning and executing services marked with the {@link JMultiCachePreloadable} annotation after application startup (or at a specific time),
 * to load their data into the cache in advance.
 *
 * @author vevoly
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JMultiCachePreloadProcessor {

    private final JMultiCacheOps jMultiCacheAdmin;
    private final JMultiCacheConfigResolver multiCacheConfigResolver;

    private static final String LOG_PREFIX = "[JMultiCache-Preload] ";

    /**
     * 执行单个缓存预热任务的逻辑。
     * <p>
     * Executes the logic for a single cache preloading task.
     *
     * @param bean    被 {@link JMultiCachePreloadable} 注解的 Spring Bean 实例。/ The Spring Bean instance annotated with {@link JMultiCachePreloadable}.
     * @param preload 注解本身的实例。/ The annotation instance itself.
     * @return 成功预热的数据条目数量；如果失败则返回 -1。/ The number of successfully preloaded data entries; returns -1 on failure.
     */
    public int process(Object bean, JMultiCachePreloadable preload) {
        if (Objects.isNull(preload)) {
            log.warn(LOG_PREFIX + "Bean {} does not have @JMultiCachePreload annotation, skipping.", bean.getClass().getSimpleName());
            return 0;
        }

        try {
            // 步骤 1: 解析配置 / Step 1: Resolve configuration
            ResolvedJMultiCacheConfig resolvedConfig = resolveConfig(bean, preload);
            if (resolvedConfig == null) {
                return -1; // 解析失败，日志已在内部打印 / Resolution failed, log already printed inside
            }
            // 步骤 2: 确定 Key 表达式 / Step 2: Determine the key expression
            String keyExpr = determineKeyExpression(preload, resolvedConfig);
            log.info(LOG_PREFIX + "Starting preload for '{}' (bean: {}, keyExpr: '{}', fetchMethod: '{}')",
                    resolvedConfig.getNamespace(), bean.getClass().getSimpleName(), keyExpr, preload.fetchMethod());
            // 步骤 3: 获取并过滤数据 / Step 3: Fetch and filter data
            List<Object> dataList = fetchData(bean, preload);
            if (CollectionUtils.isEmpty(dataList)) {
                log.warn(LOG_PREFIX + "No data to preload for '{}'.", resolvedConfig.getNamespace());
                return 0;
            }
            // 步骤 4: 构建待预热的数据 Map / Step 4: Build the data map for preloading
            Map<String, Object> dataToPreload = JMultiCacheInternalHelper.groupDataForPreload(dataList, resolvedConfig, keyExpr);
            // 步骤 5: 写入缓存 / Step 5: Write to cache
            int count = jMultiCacheAdmin.preloadMultiCache(resolvedConfig.getName(), dataToPreload);
            log.info(LOG_PREFIX + "Preload for '{}' completed, successfully cached {} items.", resolvedConfig.getNamespace(), count);
            return count;
        } catch (Exception e) {
            log.error(LOG_PREFIX + "An unexpected error occurred while processing preload for bean '{}'.", bean.getClass().getSimpleName(), e);
            return -1;
        }
    }

    /**
     * 解析并获取最终生效的缓存配置对象。
     * <p>
     * Resolves and retrieves the final effective cache configuration object.
     */
    private ResolvedJMultiCacheConfig resolveConfig(Object bean, JMultiCachePreloadable preload) {
        try {
            String configName = preload.configName();
            if (!StringUtils.hasText(configName)) {
                configName = JMultiCacheInternalHelper.inferConfigNameFromClass(bean);
                if (!StringUtils.hasText(configName)) {
                    log.error(LOG_PREFIX + "Could not auto-infer configName for bean '{}', skipping preload.", bean.getClass().getSimpleName());
                    return null;
                }
                log.info(LOG_PREFIX + "ConfigName not specified for bean '{}', auto-inferred as: {}", bean.getClass().getSimpleName(), configName);
            }
            return multiCacheConfigResolver.resolve(configName);
        } catch (Exception e) {
            log.error(LOG_PREFIX + "Failed to resolve cache configuration for bean '{}': {}", bean.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 确定用于从数据实体中提取 Key 的表达式。
     * <p>
     * Determines the expression used to extract keys from data entities.
     * 优先级 (Priority): @JMultiCachePreload -> YML config -> "null" (global key)
     */
    private String determineKeyExpression(JMultiCachePreloadable preload, ResolvedJMultiCacheConfig config) {
        if (StringUtils.hasText(preload.keyField())) {
            return preload.keyField();
        }
        if (StringUtils.hasText(config.getKeyField())) {
            log.info(LOG_PREFIX + "keyField not specified in annotation for '{}', using keyField from YML config: '{}'",
                    config.getName(), config.getKeyField());
            return config.getKeyField();
        }
        log.info(LOG_PREFIX + "keyField not specified in annotation or YML for '{}', using a global key ('null').", config.getName());
        return "null"; // 兜底为 'null' 字符串，表示所有数据聚合到一个 key / Fallback to "null" string, indicating all data aggregates to a single key.
    }

    /**
     * 从 Bean 中调用指定方法以获取源数据，并根据需要执行过滤。
     * <p>
     * Invokes the specified method on the bean to fetch source data and applies filtering if necessary.
     */
    @SuppressWarnings("unchecked")
    private List<Object> fetchData(Object bean, JMultiCachePreloadable preload) throws Exception {
        String fetchMethod = preload.fetchMethod();
        String filterExpr = preload.filter();
        // 调用数据查询方法 / Invoke the data query method
        Method method = bean.getClass().getMethod(fetchMethod);
        List<Object> dataList = (List<Object>) method.invoke(bean);
        // 如果指定了自定义 fetchMethod，则忽略 filter (约定优于配置) / If a custom fetchMethod is specified, ignore the filter (convention over configuration).
        if (!JMultiCacheConstants.DEFAULT_FETCH_METHOD_NAME.equals(fetchMethod) && StringUtils.hasText(filterExpr)) {
            log.warn(LOG_PREFIX + "Annotation specifies both fetchMethod='{}' and filter='{}', the filter will be ignored.", fetchMethod, filterExpr);
            return dataList;
        }
        // 执行过滤 / Apply filter
        if (StringUtils.hasText(filterExpr)) {
            return dataList.stream()
                    .filter(obj -> JMultiCacheInternalHelper.matchFilter(obj, filterExpr))
                    .collect(Collectors.toList());
        }
        return dataList;
    }
}
