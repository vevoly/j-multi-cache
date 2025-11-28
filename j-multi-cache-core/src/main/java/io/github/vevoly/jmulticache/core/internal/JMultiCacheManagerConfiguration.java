package io.github.vevoly.jmulticache.core.internal;

import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * JMultiCacheManager 配置类。
 * @author vevoly
 */
@Configuration
public class JMultiCacheManagerConfiguration {

    @Bean
    public JMultiCache jMultiCache(
            RedisClient redisClient,
            @Qualifier("jMultiCacheCaffeineManager") CacheManager caffeineCacheManager,
            JMultiCacheConfigResolver configResolver,
            @Qualifier("jMultiCacheAsyncExecutor") Executor asyncExecutor,
            List<RedisStorageStrategy<?>> strategies
    ) {
        return new JMultiCacheImpl(
                redisClient, caffeineCacheManager, configResolver, asyncExecutor, strategies
        );
    }
}
