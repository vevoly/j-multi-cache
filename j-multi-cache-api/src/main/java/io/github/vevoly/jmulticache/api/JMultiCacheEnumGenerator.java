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

    /**
     * 自定义生成枚举。
     *
     * @param projectRootPath 项目根目录 (例如: System.getProperty("user.dir"))
     * @param resourcesDir    资源目录相对路径 (例如: "src/main/resources")
     * @param ymlFileName     配置文件名 (例如: "application.yml")
     * @param outputDir       输出目录 (例如: "src/main/java/com/example/enums/")
     * @param packageName     包名 (例如: "com.example.enums")
     * @param className       类名 (例如: "CacheNames")
     */
    void generateEnum(String projectRootPath, String resourcesDir, String ymlFileName, String outputDir, String packageName, String className) throws IOException;
}
