package io.github.vevoly.jmulticache.api.structure;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用于封装 {@code readUnion} 方法返回结果的包装类。
 * @param <E> 集合中元素的类型。
 */
@Getter
@AllArgsConstructor
public class UnionReadResult<E> {

    /** L2 中存在的 keys 计算出的并集结果。 */
    private final Set<E> unionResult;

    /** 在 L2 中不存在的 keys 列表。 */
    private final List<String> missedKeys;

    public static <E> UnionReadResult<E> empty() {
        return new UnionReadResult<E>(Collections.emptySet(), Collections.emptyList());
    }
}
