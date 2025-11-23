package io.github.vevoly.jmulticache.api;

import java.io.IOException;

/**
 * 多级缓存枚举生成 API
 * @author vevoly
 */
public interface JMultiCacheEnumGenerator {
    /**
     * 生成多级缓存枚举
     */
    void generateEnum() throws IOException;

    /**
     * 自定义生成
     *
     * @param projectRootPath 项目根路径（绝对路径）
     * @param ymlConfigPath YML 配置路径（相对或绝对）
     * @param outputDir 生成的枚举文件输出目录
     * @param packageName 枚举类所在包名
     * @param className 枚举类名
     */
    void generateEnum(String projectRootPath, String ymlConfigPath, String outputDir, String packageName, String className) throws IOException;
}
