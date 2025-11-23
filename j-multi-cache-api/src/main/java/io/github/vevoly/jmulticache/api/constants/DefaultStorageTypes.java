package io.github.vevoly.jmulticache.api.constants;

/**
 * 定义了框架内置的默认存储类型标识符。
 * <p>
 * 用户可以在 application.yml 的 {@code storage-type} 属性中使用这些值。
 * 自定义策略可以返回任何非此列表中的字符串来标识自己。
 * <p>
 * Defines the built-in default storage type identifiers for the framework.
 * Users can use these values in the {@code storage-type} property of application.yml.
 * Custom strategies can return any string not in this list to identify themselves.
 *
 * @author vevoly
 */
public final class DefaultStorageTypes {

    /**
     * 私有构造函数，防止实例化。
     * <p>
     * Private constructor to prevent instantiation.
     */
    private DefaultStorageTypes() {}

    /**
     * 对应单个值，通常使用 Redis String 存储。
     * 这个值可以是一个简单的 Java 类型，也可以是一个被序列化为 JSON 的复杂对象。
     * <p>
     * Corresponds to a single value, typically stored in a Redis String.
     * This can be a simple Java type or a complex object serialized to JSON.
     */
    public static final String STRING = "STRING";

    /**
     * 对应 Java 的 List 结构，通常使用 Redis List 存储。
     * <p>
     * Corresponds to a Java List structure, typically stored in a Redis List.
     */
    public static final String LIST = "LIST";

    /**
     * 对应 Java 的 Set 结构，通常使用 Redis Set 存储。
     * <p>
     * Corresponds to a Java Set structure, typically stored in a Redis Set.
     */
    public static final String SET = "SET";

    /**
     * 对应 Java 的 Map<K, V> 结构，通常使用 Redis Hash 存储。
     * <p>
     * Corresponds to a Java Map<K, V> structure, typically stored in a Redis Hash.
     */
    public static final String HASH = "HASH";

    /**
     * 对应分页结构，例如 MyBatis-Plus 的 Page<T>，通常序列化后使用 Redis String 存储。
     * <p>
     * Corresponds to a pagination structure, e.g., Page<T>, typically stored as a Redis String after serialization.
     */
    public static final String PAGE = "PAGE";
}
