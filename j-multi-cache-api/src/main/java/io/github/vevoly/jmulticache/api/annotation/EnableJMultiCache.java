package io.github.vevoly.jmulticache.api.annotation;

import io.github.vevoly.jmulticache.api.config.JMultiCacheImportsSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用 j-multi-cache 框架的核心功能。
 * 将此注解添加到您的主应用类 (带有 @SpringBootApplication 的类) 上。
 * <p>
 * Enables the core functionalities of the j-multi-cache framework.
 * Add this annotation to your main application class (the one with @SpringBootApplication).
 *
 * @author vevoly
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(JMultiCacheImportsSelector.class)
public @interface EnableJMultiCache {

    /**
     * 是否启用缓存预加载功能。
     * 如果设置为 false，即使代码中存在 @CachePreload 注解，框架也不会在启动时执行预加载。
     * <p>
     * Whether to enable the cache preloading feature.
     * If set to false, the framework will not perform preloading on startup, even if @CachePreload annotations are present.
     */
    boolean preload() default true; // 默认为开启
}
