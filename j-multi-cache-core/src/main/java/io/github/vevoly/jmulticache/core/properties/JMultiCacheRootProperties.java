package io.github.vevoly.jmulticache.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 映射 application.yml 文件中 {@code j-multi-cache} 根配置块的属性。
 * <p>
 * Maps the properties of the {@code j-multi-cache} root configuration block from the application.yml file.
 *
 * @author vevoly
 */
@Data
@ConfigurationProperties(prefix = "j-multi-cache")
public class JMultiCacheRootProperties {

    /**
     * 全局默认配置。此处定义的属性将作为所有未显式指定相应属性的缓存的默认值。
     * <p>
     * Global default configurations. Properties defined here will serve as defaults for all caches that do not explicitly specify them.
     */
    private JMultiCacheProperties defaults = new JMultiCacheProperties();

    /**
     * 所有独立缓存配置的集合。Map 的 Key 是缓存的唯一名称，Value 是该缓存的具体配置。
     * <p>
     * A collection of all individual cache configurations. The map's key is the unique name of the cache, and the value is its specific configuration.
     */
    private Map<String, JMultiCacheProperties> configs = new HashMap<>();

}
