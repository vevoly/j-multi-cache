package io.github.vevoly.jmulticache.starter.autoconfigure;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redisson 配置
 * @author vevoly
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisProperties.class)
public class JMultiCacheRedissonConfiguration {

    @Bean("jMultiCacheRedissonClient")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient jMultiCacheRedissonClient(RedisProperties redisProperties) {
        Config config = new Config();

        // 强制使用 StringCodec
        config.setCodec(new StringCodec());

        // 解析地址
        String prefix = "redis://";
        if (redisProperties.getSsl().isEnabled()) {
            prefix = "rediss://";
        }

        // 拼接地址
        String address = prefix + redisProperties.getHost() + ":" + redisProperties.getPort();

        // 配置单机模式
        // 设置默认超时时间
        int timeout = 3000;
        if (redisProperties.getTimeout() != null) { // 如果用户设置了超时时间
            timeout = (int) redisProperties.getTimeout().toMillis();
        }
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase())
                .setPassword(redisProperties.getPassword())
                .setTimeout(timeout)
                .setConnectTimeout(10000);
        return Redisson.create(config);
    }
}
