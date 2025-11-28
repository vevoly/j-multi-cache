package io.github.vevoly.jmulticache.api.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stream 操作工具类
 * @author vevoly
 */
public class StreamUtils {

    private StreamUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 从 List 中提取某个字段形成新 List
     * List<Long> ids = StreamUtils.toFieldList(users, UserEntity::getId);
     *
     * @param list   原始列表
     * @param mapper 实体转字段的方法
     * @param <T>    实体类型
     * @param <R>    字段类型
     * @return 提取后的字段列表
     */
    public static <T, R> List<R> toFieldList(List<T> list, Function<T, R> mapper) {
        if (list == null) {

            // 返回一个空的可变列表
            return new ArrayList<>();
        }
        return list.stream()
                .map(mapper)

                // 强制 Stream 将结果收集到一个新的 ArrayList 中，确保可变性
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static <T, R> List<R> getFieldList(List<T> list, Function<T, R> mapper) {
        return toFieldList(list, mapper);
    }

    /**
     * 【不推荐】通过反射从实体对象列表中提取名为 "id" 的字段值。
     * 注意：此方法依赖于实体类中必须有名为 "id" 的字段或 "getId()" 的方法。
     *
     * 示例：
     *     List<UserEntity> users = ...;
     *     List<Long> ids = StreamUtils.getIdList(users);
     *
     *
     * @param list 原始实体列表
     * @param <T>  实体类型
     * @param <I>  ID 的类型 (会被强制转换)
     * @return ID 列表
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T, I> List<I> getIdList(List<T> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        List<I> idList = new ArrayList<>(list.size());
        if (list.get(0) == null) {
            return idList;
        }

        try {
            // 优先尝试调用 getId() 方法
            Method getIdMethod = list.get(0).getClass().getMethod("getId");
            for (T item : list) {
                if (item != null) {
                    idList.add((I) getIdMethod.invoke(item));
                }
            }
            return idList;
        } catch (NoSuchMethodException e) {
            // 如果没有 getId() 方法，再尝试访问 id 字段
            try {
                Field idField = list.get(0).getClass().getDeclaredField("id");
                idField.setAccessible(true);
                for (T item : list) {
                    if (item != null) {
                        idList.add((I) idField.get(item));
                    }
                }
                return idList;
            } catch (Exception ex) {
                throw new IllegalArgumentException("无法在实体类 " + list.get(0).getClass().getName() + " 中找到 'getId()' 方法或 'id' 字段。", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("通过反射获取ID时出错", e);
        }
    }

    /**
     * 按 key 分组并对分组内元素排序
     * @param list 			原始列表
     * @param keyMapper 	key 提取器
     * @param comparator 	比较器
     * @param isAsc 		是否正序排列
     * @param isSort 		是否需要排序
     *
     * Map<String, List<TenantRechargeTutorialEntity>> grouped =
     * 				StreamUtils.groupAndSort(allEntities,
     * 						e -> MultiCacheHelper.getCacheKey(config, e.getTenantId(), String.valueOf(e.getTenantId())),
     * 						Comparator.comparing(TenantRechargeTutorialEntity::getSort),
     * 						true);
     */
    public static <T, K> Map<K, List<T>> groupAndSort(
            List<T> list,
            Function<T, K> keyMapper,
            Comparator<T> comparator,
            boolean isAsc,
            boolean isSort) {

        if (list == null || list.isEmpty()) {
            return new HashMap<>();
        }
        return list.stream()
                .collect(Collectors.groupingBy(
                        keyMapper,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                l -> {
                                    if (!isSort) {
                                        return l; // 不排序，直接返回
                                    }
                                    return l.stream()
                                            .sorted(isAsc ? comparator : comparator.reversed())
                                            .toList();
                                }
                        )
                ));
    }

    public static <T, K> Map<K, List<T>> groupAndSortDesc(
            List<T> list, Function<T, K> keyMapper, Comparator<T> comparator) {
        return groupAndSort(list, keyMapper, comparator, false, true);
    }

    public static <T, K> Map<K, List<T>> groupAndSortAsc(
            List<T> list, Function<T, K> keyMapper, Comparator<T> comparator) {
        return groupAndSort(list, keyMapper, comparator, true, true);
    }

    public static <T, K> Map<K, List<T>> group(List<T> list, Function<T, K> keyMapper) {
        return groupAndSort(list, keyMapper, null, true, false);
    }

    public static <V, K> Map<K, V> listToMap(Collection<V> collection, Function<V, K> key) {
        if (collection == null || collection.isEmpty()) {
            return new HashMap<>();
        }
        return collection.stream().filter(Objects::nonNull).collect(Collectors.toMap(key, Function.identity(), (l, r) -> l));
    }

    /**
     * Map 转 List：
     *  - 如果 value 是实体对象，返回实体列表
     *  - 如果 value 是 List，自动打平合并为一个大 List
     */
    public static <K, V> List<V> mapToList(Map<K, ?> map) {
        if (map == null) return List.of();

        return map.values().stream()
                .flatMap(value -> {
                    if (value instanceof List<?> list) {
                        return list.stream();
                    } else {
                        return Stream.of(value);
                    }
                })
                .map(v -> (V) v)
                .toList();
    }

    /**
     * List 按字段去重（保留第一个）
     *
     * 			List<TenantRechargeTutorialEntity> distinctList =
     *         StreamUtils.distinctByKey(allEntities, TenantRechargeTutorialEntity::getTenantId);
     */
    public static <T, K> List<T> distinctByKey(
            List<T> list,
            Function<T, K> keyExtractor) {

        Set<K> seen = new HashSet<>();
        return list.stream()
                .filter(e -> seen.add(keyExtractor.apply(e)))
                .toList();
    }

    /**
     * 从 Map 安全获取值，避免 NPE
     *
     * 			TenantRechargeTutorialEntity entity =
     *         StreamUtils.getOrDefault(entityMap, 123L, new TenantRechargeTutorialEntity());
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * 通用排序
     *
     * @param list      原始集合
     * @param keyMapper 排序字段的 getter
     * @param comparator 比较器
     * @return 排序后的 List（不会修改原始 List）
     */
    public static <T, U extends Comparable<? super U>> List<T> sort(
            List<T> list,
            Function<? super T, ? extends U> keyMapper,
            Comparator<U> comparator
    ) {
        return list == null ? List.of() :
                list.stream()
                        .sorted(Comparator.comparing(keyMapper, comparator))
                        .collect(Collectors.toList());
    }

    /**
     * 正序排序
     *
     * @param list      原始集合
     * @param keyMapper 排序字段的 getter
     * @return 排序后的 List
     */
    public static <T, U extends Comparable<? super U>> List<T> sortAsc(
            List<T> list,
            Function<? super T, ? extends U> keyMapper
    ) {
        return sort(list, keyMapper, Comparator.naturalOrder());
    }

    /**
     * 倒序排序
     *
     * @param list      原始集合
     * @param keyMapper 排序字段的 getter
     * @return 排序后的 List
     */
    public static <T, U extends Comparable<? super U>> List<T> sortDesc(
            List<T> list,
            Function<? super T, ? extends U> keyMapper
    ) {
        return sort(list, keyMapper, Comparator.reverseOrder());
	}

}
