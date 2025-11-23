package io.github.vevoly.jmulticache.api.annotation;

import io.github.vevoly.jmulticache.api.constants.DefaultStoragePolicies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 查询并缓存
 * 支持基于 MultiCacheConfig 自动推导缓存配置与 key
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JMultiCacheable {

    /**
     * 缓存配置的名称，必须与 YML 文件中的 Key 完全匹配。
     * 如果留空，框架将尝试根据类名自动推断。
     */
    String configName() default "";

    /**
     * key 表达式或参数名（支持SpEL）
     */
    String key() default "";

    /**
     * 自定义缓存命名空间（可选，覆盖配置中的 namespace）
     */
    String namespace() default "";

    /**
     * Redis 过期时间（秒，覆盖配置）
     */
    long redisTtl() default -1;

    /**
     * 本地缓存过期时间（秒，覆盖配置）
     */
    long localTtl() default -1;

    /**
     * 本地缓存最大容量（覆盖配置）
     */
    long localMaxSize() default -1;

    /**
     * 存储策略（可覆盖配置文件）
     */
    String storagePolicy() default DefaultStoragePolicies.L1_L2_DB;

    /**
     * 是否强制刷新缓存
     */
    boolean forceRefresh() default false;
}
