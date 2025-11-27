package io.github.vevoly.jmulticache.starter.autoconfigure;

import io.github.vevoly.jmulticache.core.strategy.impl.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        StringStorageStrategy.class,
        ListStorageStrategy.class,
        SetStorageStrategy.class,
        HashStorageStrategy.class,
        PageStorageStrategy.class
})
public class JMultiCacheStrategyConfiguration {
}
