package io.github.vevoly.jmulticache.api.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import static io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants.REGISTRAR_CLASS_NAME;

/**
 * 用于导入 core 模块中的 JMultiCacheEnableRegistrar 类，避免强依赖。
 * 由于 api 模块不能依赖 core 模块（通常是 core 依赖 api），所以在 api 模块的注解中，无法直接 import 位于 core 模块的 JMultiCacheEnableRegistrar.class。
 * 所以使用 ImportSelector 返回全限定类名。
 * Spring 的 @Import 注解支持导入一个 ImportSelector 接口的实现类。在这个实现类中，通过返回字符串数组的方式，指定要加载的类（即 core 模块里的 Registrar）。
 * 这样在编译期不需要引用 core 的类，但在运行期（Runtime）只要 core 包在 classpath 下，就能正常加载。
 *
 * @author vevoly
 */
public class JMultiCacheImportsSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // Spring 会在运行时根据这个字符串去加载类
        return new String[]{REGISTRAR_CLASS_NAME};
    }
}
