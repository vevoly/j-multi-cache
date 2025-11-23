package io.github.vevoly.jmulticache.starter.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheProperties;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheRootProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

/**
 * Caffeine 本地缓存的配置类。
 * <p>
 * 此类负责读取 YML 配置，并为每一个配置了 L1 缓存的项，在 CaffeineCacheManager 中注册一个独立的 Cache 实例。
 * 它实现了 L1 缓存的按需加载和差异化 TTL 配置。
 * <p>
 * Configuration class for Caffeine local cache.
 * This class is responsible for reading YML configuration and registering an independent Cache instance in CaffeineCacheManager for each item with L1 cache configured.
 * It implements on-demand loading and differentiated TTL configuration for L1 cache.
 *
 * @author vevoly
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnClass(Caffeine.class)
public class JMultiCacheCaffeineConfiguration {

    private final JMultiCacheRootProperties rootProperties;
    private final JMultiCacheConfigResolver configResolver;

    /**
     * 配置 CaffeineCacheManager Bean。
     * <p>
     * Configures the CaffeineCacheManager Bean.
     *
     * @return 已配置好的 CacheManager 实例 / The configured CacheManager instance.
     */
    @Bean("jMultiCacheCaffeineManager")
    @ConditionalOnMissingBean(name = "jMultiCacheCaffeineManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();

        // 1. 设置全局默认配置 / Set global default configuration
        // 优先级: YML defaults > 框架常量 / Priority: YML defaults > Framework Constants
        JMultiCacheProperties defaults = rootProperties.getDefaults();

        Duration defaultLocalTtl = Optional.ofNullable(defaults)
                .map(JMultiCacheProperties::getLocalTtl)
                .orElse(Duration.ofSeconds(JMultiCacheConstants.DEFAULT_LOCAL_TTL));

        Long defaultLocalMaxSize = Optional.ofNullable(defaults)
                .map(JMultiCacheProperties::getLocalMaxSize)
                .orElse(JMultiCacheConstants.DEFAULT_LOCAL_CACHE_MAX_SIZE);

        // 设置默认的 Caffeine 构建器，用于那些没有特殊配置的缓存名 / Set the default Caffeine builder for cache names without specific configuration
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(defaultLocalTtl)
                .maximumSize(defaultLocalMaxSize)
                .recordStats()
        );

        // 2. 遍历从 YML 解析出的所有配置，注册自定义缓存 / Iterate through all configurations parsed from YML and register custom caches
        int count = 0;
        for (ResolvedJMultiCacheConfig config : configResolver.getAllResolvedConfigs()) {
            Duration localTtl = config.getLocalTtl();

            // 如果 localTtl 为空或为0，表示该配置不使用 L1 本地缓存 / If localTtl is null, zero, or negative, it means this configuration does not use L1 local cache
            if (localTtl == null || localTtl.isZero() || localTtl.isNegative()) {
                continue;
            }

            log.info("[JMultiCache-Caffeine] Registering custom cache: '{}', expire={}s, maxSize={}",
                    config.getNamespace(), localTtl.getSeconds(), config.getLocalMaxSize());

            caffeineCacheManager.registerCustomCache(
                    config.getNamespace(),
                    Caffeine.newBuilder()
                            .expireAfterWrite(localTtl)
                            .maximumSize(config.getLocalMaxSize())
                            .recordStats()
                            .build()
            );
            count++;
        }

        log.info("[JMultiCache-Caffeine] Initialized with {} custom L1 caches.", count);
        return caffeineCacheManager;
    }
}
