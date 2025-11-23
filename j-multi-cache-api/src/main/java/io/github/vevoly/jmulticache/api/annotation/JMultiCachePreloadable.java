package io.github.vevoly.jmulticache.api.annotation;

import io.github.vevoly.jmulticache.api.JMultiCacheConfigName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 緩存預熱注解
 * @Author： Karami
 *
 * <h3>多级缓存预热注解</h3>
 *
 * 用于在应用启动时（或手动触发时）自动将数据库中的数据加载进多级缓存（Redis + 本地 Caffeine）。
 *
 * <p>推荐写法示例：</p>
 * <pre>
 * @CachePreloadable(
 *     config = MultiCacheEnum.TENANT_APP_VERSION,   // 缓存配置枚举
 *     key = "tenantId",                             // 分组字段，可省略，默认读取枚举里的 key
 *     filter = "delFlag == 0"                       // 自动过滤条件，可选
 * )
 * public class TenantAppVersionServiceImpl extends ServiceImpl<...> {
 *     public List<TenantAppVersionEntity> list() {
 *         return this.list();
 *     }
 * }
 * </pre>
 *
 * <h4>行为说明</h4>
 * <ul>
 *     <li>缓存配置（过期时间、类型、namespace、valueClass 等）统一从 {@link JMultiCacheConfigName} 获取。</li>
 *     <li>{@code key} 可选：
 *          <ul>
 *              <li>若注解中指定，则使用注解值。</li>
 *              <li>若未指定，则使用 yml KeyField} ()}。</li>
 *              <li>若两者都为空，则使用默认 "null"（全局 key）。</li>
 *          </ul>
 *     </li>
 *     <li>{@code filter} 为可选字段过滤表达式，仅在 fetchMethod = "list" 时生效。
 *         语法示例：<code>delFlag == 0</code>、<code>status != 1</code></li>
 *     <li>{@code fetchMethod} 默认为 "list"，应返回一个 List&lt;Entity&gt;。</li>
 *     <li>缓存类型自动根据 yml 推断，不需手动指定 valueType。</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface JMultiCachePreloadable {

    /**
     * 缓存配置的名称，必须与 YML 文件中的 Key 完全匹配。
     * 如果留空，框架将尝试根据类名自动推断。
     */
    String configName() default "";

    /**
     * 缓存分组字段
     * 用于分组生成缓存 Key 的字段名
     * key 支持 SpEL 表达式；可为空，若为空则从 MultiCacheEnum.keyField 中读取
     * 比如 entity.userTypeId
     * 支持 3 种 key 模式
     * 1. 字段模式： 使用实体的字段作为 key， key = "tenantId"
     * 2. 固定值模式： 所有数据使用同一个固定 key， key = "#'global'" 或 key = "#'null'"
     * 3. 多字段拼接模式： 使用 SpEL 表达式组合多个字段， key = "#tenantId + ':' + #userId"
     * 详见 CachePreloadProcessor
     */
    String keyField() default "";

    /**
     * 作为缓存内容的字段名（可选）只缓存对象里的某个字段值，而不是整个对象
     * 比如 entity.bannerId
     * 默认缓存整个对象
     */
    String valueField() default "";

    /**
     * 可选：过滤条件表达式，（仅支持 == 与 !=）
     * 多个过滤条件可使用 && ，例如 "status == 0 && delFlag == 0"
     */
    String filter() default "";

    /**
     * 可选：自定义取数据方法，默认调用 list()
     * 方法需为 public、无参、返回 List
     * 用于构建更为复杂的 sql语句
     * 注意：fetchMethod 对应的方法必须是 public
     * 这个参数和 filter 应该时互斥关系，可以以说这个参数里包含了 filter
     * 示例：
     *
     * @CachePreload(config = MultiCacheEnum.TG_ROBOT_DEFAULT, fetchMethod = "one")
     */
    String fetchMethod() default "list";

}