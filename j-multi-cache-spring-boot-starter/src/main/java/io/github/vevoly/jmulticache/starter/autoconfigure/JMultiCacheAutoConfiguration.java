package io.github.vevoly.jmulticache.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.internal.JMultiCacheManagerConfiguration;
import io.github.vevoly.jmulticache.core.internal.JMultiCacheableAspect;
import io.github.vevoly.jmulticache.core.internal.NoOpJMultiCacheManager;
import io.github.vevoly.jmulticache.core.processor.JMultiCachePreloadProcessor;
import io.github.vevoly.jmulticache.core.properties.JMultiCacheRootProperties;
import io.github.vevoly.jmulticache.core.redis.RedissonRedisClient;
import io.github.vevoly.jmulticache.core.redis.listener.JMultiCacheMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants.J_MULTI_CACHE_EVICT_TOPIC;
import static io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants.MARKER_CONFIG_CLASS_NAME;

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
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, Caffeine.class})
@EnableConfigurationProperties(JMultiCacheRootProperties.class)
@AutoConfigureBefore(org.redisson.spring.starter.RedissonAutoConfiguration.class)
@ConditionalOnProperty(prefix = "j-multi-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JMultiCacheAutoConfiguration {

    /**
     * 【启用模式】
     * 用户使用了 @EnableJMultiCache 注解。
     * 此时加载真实的组件：线程池、Redis连接、AOP、预热器等。
     */
    @Configuration
    @ConditionalOnBean(type = MARKER_CONFIG_CLASS_NAME) // 只有 Marker 存在时才生效 / Only take effect when Marker exists
    @Import({
            JMultiCacheableAspect.class,                // AOP 切面 / AOP aspect
            JMultiCachePreloadProcessor.class,          // 预热执行器 / Preload executor
            JMultiCacheManagerConfiguration.class,      // 真实 Manager / Real Manager
            JMultiCacheStrategyConfiguration.class,     // 策略组 / Strategy group
            JMultiCacheCaffeineConfiguration.class,     // Caffeine 配置  / Caffeine configuration
            JMultiCacheRedissonConfiguration.class,     // Redisson 配置 (StringCodec) / Redisson configuration (StringCodec)
            JMultiCachePreloadAutoConfiguration.class,  // 预热调度器 (Runner) / Preload scheduler (Runner)
    })
    static class JMultiCacheActiveConfiguration {

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
        public RedisClient redisClient(@Qualifier("jMultiCacheRedissonClient") RedissonClient redissonClient,
                                       @Qualifier("jMultiCacheObjectMapper") ObjectMapper objectMapper) {
            return new RedissonRedisClient(redissonClient, objectMapper);
        }

        /**
         * 5. 配置
         * @param connectionFactory
         * @param jMultiCacheOps
         * @param objectMapper
         * @return
         */
        @Bean
        public RedisMessageListenerContainer jMultiCacheRedisContainer(
                RedisConnectionFactory connectionFactory,
                JMultiCacheOps jMultiCacheOps,
                @Qualifier("jMultiCacheObjectMapper") ObjectMapper objectMapper) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            // 注册监听器
            JMultiCacheMessageListener listener = new JMultiCacheMessageListener(objectMapper, jMultiCacheOps);
            container.addMessageListener(listener, new ChannelTopic(J_MULTI_CACHE_EVICT_TOPIC));
            return container;
        }
    }

    /**
     * 【降级模式】
     * 用户没有使用 @EnableJMultiCache 注解。
     * 此时不加载任何重资源（Redis/Thread/AOP），只注册一个空实现的 Manager，防止报错。
     */
    @Configuration
    @ConditionalOnMissingBean(type = MARKER_CONFIG_CLASS_NAME) // Marker 不存在时生效 / Only take effect when Marker not exists
    static class JMultiCacheFallbackConfiguration {

        @Bean
        @ConditionalOnMissingBean(JMultiCache.class)
        public JMultiCache jMultiCacheFallback() {
            // 返回空实现，所有方法直接透传 DB，不走缓存
            return new NoOpJMultiCacheManager();
        }
    }

}