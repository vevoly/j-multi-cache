package io.github.vevoly.jmulticache.core.wrap;

import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 多级缓存批量查询的结果包装类。
 * <p>
 * 这是一个框架内部使用的辅助类，用于封装最完整的、按 Key 分组的数据结构。
 * 它在构造时会立即根据缓存的 {@code storageType}，预先计算好两种常见的数据视图：
 * 1. {@link #getGroupedMap()}: 保留了原始分组结构的 Map。
 * 2. {@link #getFlatList()}: 将所有分组中的值“打平”成一个单一的 List。
 * 这使得上层调用者可以按需获取所需的数据格式，而无需关心结果塑形的复杂逻辑。
 * <p>
 * A result wrapper class for multi-level cache batch queries.
 * This is an internal helper class used by the framework to encapsulate the complete, key-grouped data structure.
 * Upon construction, it immediately pre-computes two common data views based on the cache's {@code storageType}:
 * 1. {@link #getGroupedMap()}: A map that preserves the original grouped structure.
 * 2. {@link #getFlatList()}: A single list created by "flattening" the values from all groups.
 * This allows the upper-level caller to retrieve the data in the desired format on demand, without worrying about the complexity of result shaping.
 *
 * @param <K> 键的类型。/ The type of the keys.
 * @param <V> 值的类型 (打平后列表中的元素类型)。/ The type of the values (the element type in the flattened list).
 * @author vevoly
 */
@Getter
public class JMultiCacheResult<K, V> {

    /**
     * 保持了原始分组结构的 Map。
     * <ul>
     *     <li>如果 storageType 是 "LIST", 则 Map 结构为 {@code Map<K, List<V>>}。</li>
     *     <li>如果 storageType 是 "STRING", 则 Map 结构为 {@code Map<K, V>}。</li>
     * </ul>
     * <p>
     * A map that preserves the original grouped structure.
     * <ul>
     *     <li>If storageType is "LIST", the map structure is {@code Map<K, List<V>>}.</li>
     *     <li>If storageType is "STRING", the map structure is {@code Map<K, V>}.</li>
     * </ul>
     */
    private final Map<K, Object> groupedMap;

    /**
     * 将所有分组中的值“打平”成一个单一的 List。
     * <p>
     * A single list created by "flattening" the values from all groups.
     */
    private final List<V> flatList;

    /**
     * 构造函数，接收最终的结果 Map 和配置，并完成两种视图的计算。
     * <p>
     * Constructor that accepts the final result map and configuration, and computes both data views.
     *
     * @param resultMap 从缓存或数据库获取的、按 Key 分组的最终数据。/ The final data retrieved from the cache or database, grouped by key.
     * @param config    当前操作的已解析配置，用于决定如何处理 {@code resultMap} 的值。/ The resolved configuration for the current operation, used to determine how to process the values of the {@code resultMap}.
     */
    @SuppressWarnings("unchecked")
    public JMultiCacheResult(Map<K, Object> resultMap, ResolvedJMultiCacheConfig config) {
        this.groupedMap = (resultMap != null) ? resultMap : Collections.emptyMap();

        if (MapUtils.isEmpty(this.groupedMap)) {
            this.flatList = Collections.emptyList();
            return;
        }

        // 使用字符串常量进行比较
        if (config != null && DefaultStorageTypes.LIST.equals(config.getStorageType())) {
            // 如果存储类型是 LIST，值的类型是 Collection<V>，需要使用 flatMap 打平
            this.flatList = this.groupedMap.values().stream()
                    .filter(Objects::nonNull)
                    // 确保值是 Collection 类型再进行流操作，增加健壮性
                    .filter(Collection.class::isInstance)
                    .flatMap(listValue -> ((Collection<V>) listValue).stream())
                    .collect(Collectors.toList());
        } else {
            // 如果存储类型是 STRING, HASH, PAGE 等，值的类型就是 V
            this.flatList = this.groupedMap.values().stream()
                    .filter(Objects::nonNull)
                    // 更安全地过滤和转换
                    .map(value -> (V) value)
                    .collect(Collectors.toList());
        }
    }
}
