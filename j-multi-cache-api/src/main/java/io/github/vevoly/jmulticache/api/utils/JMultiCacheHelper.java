package io.github.vevoly.jmulticache.api.utils;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;

import java.util.*;
import java.util.function.Function;

/**
 * j-multi-cache 框架的公共辅助工具类。
 * <p>
 * 此类提供了一些对框架使用者（特别是自定义策略的实现者）有用的静态便利方法。
 * <p>
 * Public helper utility class for the j-multi-cache framework.
 * This class provides static convenience methods that are useful for framework users,
 * especially for those implementing custom strategies.
 *
 * @author vevoly
 */
public final class JMultiCacheHelper {

    private JMultiCacheHelper() {}

    /**
     * 拼接缓存key
     * @param namespace
     * @param key
     * @return
     */
    public static String buildKey(String namespace, String... key) {
        if (namespace == null) {
            throw new IllegalArgumentException("namespace 不能为空");
        }
        if (key == null || key.length == 0) {
            return namespace;
        }
        return namespace + ":" + String.join(":", key);
    }

    /**
     * 判断一个缓存结果是否是框架定义的“空值标记”。
     * <p>
     * 此方法会检查对象本身、或者其作为集合/Map 时的内容，是否符合空值标记的约定。
     * 它会从传入的配置对象中动态获取用户自定义的空值标记字符串。
     * <p>
     * Checks if a cached result is the framework-defined "null value marker".
     * This method checks if the object itself, or its content as a Collection/Map,
     * conforms to the null value marker convention. It dynamically retrieves the user-defined
     * null marker string from the provided configuration object.
     *
     * @param result 待检查的缓存结果。/ The cached result to check.
     * @param config 当前操作的已解析配置。如果为 null，则使用框架的默认空值标记。/
     *               The resolved configuration for the current operation. If null, the framework's default null marker is used.
     * @return {@code true} 如果结果是空值标记。/ {@code true} if the result is a null value marker.
     */
    public static boolean isSpecialEmptyData(Object result, ResolvedJMultiCacheConfig config) {
        if (result == null) {
            return false;
        }
        String emptyValueMark = (config != null) ? config.getEmptyValueMark() : JMultiCacheConstants.EMPTY_CACHE_VALUE;

        if (emptyValueMark.equals(result)) {
            return true;
        }
        if (result instanceof Collection<?> collection) {
            // 空集合本身也可能被视为空结果，但“特殊标记”是指包含占位符的集合
            return collection.size() == 1 && emptyValueMark.equals(collection.iterator().next());
        }
        if (result instanceof Map<?, ?> map) {
            // 空 Map 本身也可能被视为空结果
            return map.size() == 1 && map.containsKey(emptyValueMark);
        }
        return false;
    }

    /**
     * 获取空值标记
     * @param config
     * @return
     */
    public static String getEmptyValueMark(ResolvedJMultiCacheConfig config) {
        return config == null ? JMultiCacheConstants.EMPTY_CACHE_VALUE : config.getEmptyValueMark();
    }

    /**
     * 判断是否为空
     * @param result
     * @return
     * @param <T>
     */
    public static <T> boolean isEmpty(T result) {
        if (result == null) return true;
        if (JMultiCacheConstants.EMPTY_CACHE_VALUE.equals(result)) return true;
        if (result instanceof Collection) return ((Collection<?>) result).isEmpty();
        if (result instanceof Map) return ((Map<?, ?>) result).isEmpty();
        return false;
    }

    /**
     * 判断从数据库直接查询的结果是否为空。
     * <p>
     * Checks if the result queried directly from the database is effectively empty.
     *
     * @param result 从数据库返回的对象。/ The object returned from the database.
     * @return {@code true} 如果对象是 null、空的 Collection 或空的 Map。/ {@code true} if the object is null, an empty Collection, or an empty Map.
     */
    public static boolean isResultEmptyFromDb(Object result) {
        if (result == null) return true;
        if (result instanceof Collection) return ((Collection<?>) result).isEmpty();
        if (result instanceof Map) return ((Map<?, ?>) result).isEmpty();
        return false;
    }

    /**
     * 将空标记转换为空数据
     * 用于数据返回
     * @param result
     * @return
     * @param <T>
     */
    @SuppressWarnings("unchecked")
    public static <T> T specialEmptyData2EmptyData(T result, ResolvedJMultiCacheConfig config) {
        if (result == null) return null;
        String emptyCacheMark = getEmptyValueMark(config);
        if (emptyCacheMark.equals(result)) {
            return null;
        }

        // 判断集合是否为只包含一个特殊标记的 List和 Set
        if (result instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return result;
            }
            if (collection.size() == 1 && emptyCacheMark.equals(collection.iterator().next())) {
                if (result instanceof List) {
                    return (T) List.of();
                }
                if (result instanceof Set) {
                    return (T) Set.of();
                }
            }
        }

        // Map 和 HashMap
        if (result instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return result;
            }
            if (map.size() == 1 && Boolean.TRUE.equals(map.get(emptyCacheMark))) {
                return (T) Map.of();
            }
        }
        return null;
    }

}
