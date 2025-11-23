package io.github.vevoly.jmulticache.core.utils;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个用于从 Class 和 storageType 动态构建 Jackson {@link TypeReference} 的工具类。
 * <p>
 * A utility class for dynamically constructing Jackson's {@link TypeReference} from a Class and a storageType.
 * @author vevoly
 */
public final class JavaTypeReference {

    private JavaTypeReference() {}

    /**
     * 根据实体类和存储类型字符串，构建对应的 TypeReference。
     */
    public static TypeReference<?> from(Class<?> entityClass, String storageType, TypeFactory typeFactory) {
        JavaType javaType;
        switch (storageType) {
            case DefaultStorageTypes.LIST:
                javaType = typeFactory.constructCollectionType(List.class, entityClass);
                break;
            case DefaultStorageTypes.SET:
                javaType = typeFactory.constructCollectionType(Set.class, entityClass);
                break;
            case DefaultStorageTypes.HASH:
                // 对于 HASH，我们假设 Key 是 String，Value 是 entityClass
                javaType = typeFactory.constructMapType(Map.class, String.class, entityClass);
                break;
            case DefaultStorageTypes.STRING:
            case DefaultStorageTypes.PAGE:
                javaType = typeFactory.constructParametricType(Page.class, entityClass);
                break;
            default:
                // 对于 STRING, PAGE, 或其他自定义类型，直接使用 entityClass 本身的类型
                javaType = typeFactory.constructType(entityClass);
                break;
        }
        return of(javaType);
    }

    /**
     * 一个 hacky 的方法，用于从 JavaType 创建一个匿名的 TypeReference 实例。
     */
    public static <T> TypeReference<T> of(JavaType javaType) {
        return new TypeReference<T>() {
            @Override
            public java.lang.reflect.Type getType() {
                return javaType;
            }
        };
    }
}
