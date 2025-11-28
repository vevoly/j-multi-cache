package io.github.vevoly.jmulticache.core.config;

import io.github.vevoly.jmulticache.api.annotation.EnableJMultiCache;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants.PRELOAD_ATTRIBUTE_NAME;

/**
 * 这个类负责解析 @EnableJMultiCache 注解，并将 JMultiCacheMarkerConfiguration 注册到 Spring 容器中
 * @author vevoly
 */
public class JMultiCacheEnableRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 1. 获取注解属性
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableJMultiCache.class.getName())
        );

        if (attributes != null) {
            boolean preload = attributes.getBoolean(PRELOAD_ATTRIBUTE_NAME);

            // 2. 构建 Marker Bean 定义
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(JMultiCacheMarkerConfiguration.class);
            builder.addPropertyValue(PRELOAD_ATTRIBUTE_NAME, preload);

            // 3. 注册到 Spring 容器
            registry.registerBeanDefinition(JMultiCacheMarkerConfiguration.class.getName(), builder.getBeanDefinition());
        }
    }
}
