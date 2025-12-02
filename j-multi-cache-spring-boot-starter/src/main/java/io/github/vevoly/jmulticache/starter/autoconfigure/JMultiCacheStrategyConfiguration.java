package io.github.vevoly.jmulticache.starter.autoconfigure;

import io.github.vevoly.jmulticache.core.strategy.impl.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 缓存策略配置
 * JMultiCache Strategy Configuration
 *
 * @author vevoly
 */
@Configuration(proxyBeanMethods = false)
@Import({
        SetStorageStrategy.class,
        ListStorageStrategy.class,
        ZSetStorageStrategy.class,
        HashStorageStrategy.class,
        PageStorageStrategy.class,
        StringStorageStrategy.class,
})
public class JMultiCacheStrategyConfiguration {
}
