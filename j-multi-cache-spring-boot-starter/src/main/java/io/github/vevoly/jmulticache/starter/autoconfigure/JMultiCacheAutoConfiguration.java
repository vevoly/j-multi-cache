package io.github.vevoly.jmulticache.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.core.aop.JMultiCacheableAspect;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.manager.JMultiCacheManager;
import io.github.vevoly.jmulticache.core.processor.JMultiCachePreloadProcessor;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheRootProperties;
import io.github.vevoly.jmulticache.core.redis.RedissonRedisClient;
import io.github.vevoly.jmulticache.core.strategy.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * j-multi-cache 的自动配置类。
 * <p>
 * 负责初始化和组装框架的所有核心组件，包括：
 * 1. 激活配置属性。
 * 2. 初始化配置解析器。
 * 3. 配置 Redis 客户端 (基于 Redisson)。
 * 4. 注册存储策略、管理器、AOP 切面和预热处理器。
 * <p>
 * Auto-configuration class for j-multi-cache.
 * Responsible for initializing and assembling all core components of the framework, including:
 * 1. Activating configuration properties.
 * 2. Initializing the configuration resolver.
 * 3. Configuring the Redis client (based on Redisson).
 * 4. Configuring Caffeine local cache (dynamically based on YML).
 * 5. Registering storage strategies, manager, AOP aspect, and preload processor.
 *
 * @author vevoly
 */
@Slf4j
@Configuration
@ConditionalOnClass({RedissonClient.class, Caffeine.class})
@EnableConfigurationProperties(JMultiCacheRootProperties.class)
@AutoConfigureAfter(org.redisson.spring.starter.RedissonAutoConfiguration.class)
@ConditionalOnProperty(prefix = "j-multi-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        StringStorageStrategy.class,
        ListStorageStrategy.class,
        SetStorageStrategy.class,
        HashStorageStrategy.class,
        PageStorageStrategy.class,
        JMultiCacheableAspect.class,
        JMultiCachePreloadProcessor.class,
        JMultiCacheCaffeineConfiguration.class
})
public class JMultiCacheAutoConfiguration {

    /**
     * 1. 配置异步线程池
     * 用于 L1 缓存的异步回填等操作。
     */
    @Bean("jMultiCacheAsyncExecutor")
    @ConditionalOnMissingBean(name = "jMultiCacheAsyncExecutor")
    public Executor jMultiCacheAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("JMultiCache-Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 2. 配置 Jackson ObjectMapper
     * 配置框架专用的 ObjectMapper，避免受用户全局配置污染
     */
    @Bean("jMultiCacheObjectMapper")
    @ConditionalOnMissingBean(name = "jMultiCacheObjectMapper")
    public ObjectMapper jMultiCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper;
    }

    /**
     * 3. 配置 Config Resolver (核心配置解析器)
     * 它必须先于 Caffeine Manager 初始化，因为它提供了缓存配置。
     */
    @Bean
    public JMultiCacheConfigResolver jMultiCacheConfigResolver(
            JMultiCacheRootProperties rootProperties,
            @Qualifier("jMultiCacheObjectMapper") ObjectMapper objectMapper
    ) {
        return new JMultiCacheConfigResolver(rootProperties, objectMapper);
    }

    /**
     * 4. 配置 RedisClient (基于 Redisson)
     * 依赖用户项目中已有的 RedissonClient Bean。
     */
    @Bean
    @ConditionalOnMissingBean(RedisClient.class)
    public RedisClient redisClient(RedissonClient redissonClient) {
        return new RedissonRedisClient(redissonClient);
    }

    /**
     * 5. 配置核心管理器 (JMultiCacheManager)
     * 它是框架的“大脑”，通过自动装配将所有组件连接起来。
     * <p>
     * 注意：strategies 参数会由 Spring 自动收集所有实现了 RedisStorageStrategy 的 Bean (包括上面 @Import 的和用户自定义的)。
     */
    @Bean
    public JMultiCacheManager jMultiCacheManager(
            RedisClient redisClient,
            @Qualifier("jMultiCacheCaffeineManager") CacheManager caffeineCacheManager,
            JMultiCacheConfigResolver configResolver,
            @Qualifier("jMultiCacheAsyncExecutor") Executor asyncExecutor,
            java.util.List<RedisStorageStrategy<?>> strategies
    ) {
        return new JMultiCacheManager(redisClient, caffeineCacheManager, configResolver, asyncExecutor, strategies);
    }
}