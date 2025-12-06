package io.github.vevoly.jmulticache.core.utils;


import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 封装批量查询前的复杂上下文初始化逻辑 (框架内部使用)。
 * <p>
 * 核心职责是确保 {@code config} 和 {@code keyBuilder} 在核心查询逻辑执行前都已准备就绪。
 * 能够处理两种主要场景：
 * 1. 由 AOP 调用，此时会传入一个功能完备的、预先构建好的 {@code keyBuilder}。
 * 2. 由用户手动调用，此时它会根据 YML 中配置的 {@code key-field} 自动生成一个 {@code keyBuilder}。
 * <p>
 * Encapsulates complex context initialization logic for batch queries (for internal framework use).
 * Its core responsibility is to ensure that both the {@code config} and {@code keyBuilder} are ready before the core query logic is executed.
 * It handles two main scenarios:
 * 1. Called by AOP, where a fully functional, pre-built {@code keyBuilder} is provided.
 * 2. Called manually by a user, where it automatically generates a {@code keyBuilder} based on the {@code key-field} configured in YML.
 *
 * @param <K> ID 的类型。/ The type of the ID.
 * @author vevoly
 */
@Slf4j
@Getter
public class JMultiCacheContextHandler<K> {

    private static final Pattern SPEL_FIELD_PATTERN = Pattern.compile("#(\\w+)");

    private Collection<K> ids;
    private String businessKey;
    private Function<K, String> keyBuilder;
    private ResolvedJMultiCacheConfig config;


    /**
     * 构造函数，接收初始参数并完成所有初始化。
     * <p>
     * Constructor that accepts initial parameters and completes all initializations.
     *
     * @param config             可选的、已解析的配置。如果为 null，将尝试从 {@code externalKeyBuilder} 推断。/ Optional resolved configuration. If null, it will be inferred from the {@code externalKeyBuilder}.
     * @param ids                要查询的 ID 集合。/ The collection of IDs to query.
     * @param businessKey        业务 key 字段名。/ The business key field name.
     * @param externalKeyBuilder 可选的、外部传入的 key 构建器 (通常由 AOP 提供)。/ Optional external key builder (typically provided by AOP).
     * @param configResolver     配置解析器，用于在需要时反向解析配置。/ The configuration resolver, used for reverse-resolving configuration when needed.
     */
    public JMultiCacheContextHandler(
            Collection<K> ids,
            String businessKey,
            ResolvedJMultiCacheConfig config,
            Function<K, String> externalKeyBuilder,
            JMultiCacheConfigResolver configResolver
    ) {
        this.ids = ids;
        this.businessKey = businessKey;
        this.config = resolveFinalConfig(config, ids, externalKeyBuilder, configResolver);
        this.keyBuilder = resolveFinalKeyBuilder(externalKeyBuilder, this.config);
    }

    /**
     * 解析出最终的 ResolvedJMultiCacheConfig。
     * <p>
     * Resolves the final ResolvedJMultiCacheConfig.
     */
    private ResolvedJMultiCacheConfig resolveFinalConfig(
            ResolvedJMultiCacheConfig config,
            Collection<K> ids, Function<K, String> externalKeyBuilder,
            JMultiCacheConfigResolver configResolver
    ) {
        if (config != null) {
            return config;
        }
        if (externalKeyBuilder == null || ids.isEmpty()) {
            throw new IllegalArgumentException("当 config 为 null 时，keyBuilder 和 ids 不能为空，无法推断配置。");
        }
        K firstId = this.ids.iterator().next();
        String sampleKey = externalKeyBuilder.apply(firstId);
        ResolvedJMultiCacheConfig resolvedConfig = configResolver.resolveFromFullKey(sampleKey);
        if (resolvedConfig == null) {
            throw new IllegalArgumentException("无法从 key 推断出 ResolvedMultiCacheConfig: " + sampleKey);
        }
        return resolvedConfig;
    }

    /**
     * 解析出最终的 KeyBuilder，保留了复杂的自动生成逻辑。
     * <p>
     * Resolves the final KeyBuilder, preserving the complex auto-generation logic.
     */
    private Function<K, String> resolveFinalKeyBuilder(
            Function<K, String> externalKeyBuilder,
            ResolvedJMultiCacheConfig finalConfig
    ) {
        // AOP 路径：如果外部传入了 keyBuilder，直接使用它 / AOP Path: If an external keyBuilder is provided, use it directly.
        if (externalKeyBuilder != null) {
            return externalKeyBuilder;
        }
        // 手动调用路径：根据 YML 中的 keyField 配置，自动生成 keyBuilder / Manual Call Path: Auto-generate keyBuilder based on the keyField from YML configuration.
        final String keyFieldExpr = finalConfig.getKeyField().trim();

        return (K id) -> {

            // keyField 未配置，使用 id 本身作为 key part / If keyField is not configured, use the ID itself as the key part.
            if (StringUtils.isBlank(keyFieldExpr)) {
                return JMultiCacheHelper.buildKey(finalConfig.getNamespace(), String.valueOf(id));
            }

            // keyField 包含 SpEL-like 或逗号分隔的复杂表达式 / If keyField is a complex expression containing SpEL-like syntax or commas.
            if (keyFieldExpr.contains(",") || keyFieldExpr.contains("+") || keyFieldExpr.contains("#")) {
                String[] fields;
                if (keyFieldExpr.contains(",")) {
                    fields = Arrays.stream(keyFieldExpr.split(",")).map(String::trim).toArray(String[]::new);
                } else {
                    Matcher matcher = SPEL_FIELD_PATTERN.matcher(keyFieldExpr);
                    List<String> fieldList = new ArrayList<>();
                    while (matcher.find()) {
                        fieldList.add(matcher.group(1));
                    }
                    fields = fieldList.isEmpty() ? new String[]{keyFieldExpr} : fieldList.toArray(new String[0]);
                }

                String[] parts = new String[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    parts[i] = JMultiCacheInternalHelper.getKeyValueSafe(id, fields[i]);
                }
                return JMultiCacheHelper.buildKey(finalConfig.getNamespace(), parts);
            }

            // keyField 是单个普通字段名 / If keyField is a single, plain field name.
            String singleValue = JMultiCacheInternalHelper.getKeyValueSafe(id, keyFieldExpr);
            return JMultiCacheHelper.buildKey(finalConfig.getNamespace(), singleValue);
        };
    }
}

