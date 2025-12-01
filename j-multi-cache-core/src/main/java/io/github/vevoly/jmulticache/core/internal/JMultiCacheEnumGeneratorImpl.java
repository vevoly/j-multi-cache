package io.github.vevoly.jmulticache.core.internal;

import io.github.vevoly.jmulticache.api.JMultiCacheEnumGenerator;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 缓存名称枚举生成器实现类。
 * <p>
 * 用于读取 application.yml 配置，自动生成 Java 枚举类。
 * 建议在单元测试中调用。
 *
 * @author vevoly
 */
public class JMultiCacheEnumGeneratorImpl implements JMultiCacheEnumGenerator {

    // 动态获取当前项目根目录 (兼容 Windows/Mac/Linux)
    private static final String PROJECT_ROOT = System.getProperty("user.dir");
    // 默认资源目录
    private static final String DEFAULT_RESOURCES_DIR = "src/main/resources";
    // 默认yml文件名
    private static final String DEFAULT_YML_FILE = "application.yml";
    // 默认包名：使用一个不会冲突的通用包名
    private static final String DEFAULT_PACKAGE = "jmulticache.generated";
    private static final String DEFAULT_CLASS = "JMultiCacheName";

    @Override
    public void generateEnum() throws IOException {
        // 使用默认值调用
        String defaultOutputDir = "src/main/java/" + DEFAULT_PACKAGE.replace(".", "/");
        generateEnum(PROJECT_ROOT, DEFAULT_RESOURCES_DIR, DEFAULT_YML_FILE, defaultOutputDir, DEFAULT_PACKAGE, DEFAULT_CLASS);
    }

    @Override
    public void generateEnum(String projectRootPath, String ymlConfigPath, String outputDir, String packageName, String className) throws IOException {
        generateEnum(projectRootPath, ymlConfigPath, DEFAULT_YML_FILE, outputDir, packageName, className);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void generateEnum(String projectRootPath,
                             String resourcesDir,
                             String ymlFileName,
                             String outputDir,
                             String packageName,
                             String className) throws IOException {

        // 1. 参数归一化 (设置默认值)
        String root = (projectRootPath == null || projectRootPath.isEmpty()) ? PROJECT_ROOT : projectRootPath;
        String resDir = (resourcesDir == null || resourcesDir.isEmpty()) ? DEFAULT_RESOURCES_DIR : resourcesDir;
        String fileName = (ymlFileName == null || ymlFileName.isEmpty()) ? DEFAULT_YML_FILE : ymlFileName;
        String pkg = (packageName == null || packageName.isEmpty()) ? DEFAULT_PACKAGE : packageName;
        String outDir = (outputDir == null || outputDir.isEmpty()) ? ("src/main/java/" + pkg.replace(".", "/")) : outputDir;
        String clzName = (className == null || className.isEmpty()) ? DEFAULT_CLASS : className;

        System.out.println("====== [JMultiCache] 开始生成枚举类: " + clzName + " ======");
        System.out.println(" - 项目根目录: " + root);
        System.out.println(" - 资源目录:   " + resDir);
        System.out.println(" - 配置文件:   " + fileName);

        // 2. 定位主配置文件
        File mainConfigFile = Paths.get(root, resDir, fileName).toFile();
        // 智能容错：如果 .yml 不存在，尝试找 .yaml
        if (!mainConfigFile.exists()) {
            if (fileName.endsWith(".yml")) {
                File yamlFile = Paths.get(root, resDir, fileName.replace(".yml", ".yaml")).toFile();
                if (yamlFile.exists()) {
                    mainConfigFile = yamlFile;
                    System.out.println(" - (自动切换到 .yaml 文件)");
                }
            } else if (fileName.endsWith(".yaml")) {
                File ymlFile = Paths.get(root, resDir, fileName.replace(".yaml", ".yml")).toFile();
                if (ymlFile.exists()) {
                    mainConfigFile = ymlFile;
                }
            }
        }
        if (!mainConfigFile.exists()) {
            throw new FileNotFoundException("未找到配置文件: " + mainConfigFile.getAbsolutePath());
        }
        // 3. 解析 YAML
        Yaml yaml = new Yaml();
        Map<String, Object> allConfigs = new LinkedHashMap<>();
        try (InputStream fis = Files.newInputStream(mainConfigFile.toPath())) {
            Map<String, Object> mainYmlMap = yaml.load(fis);
            if (mainYmlMap == null) mainYmlMap = Collections.emptyMap();
            // 3.1 解析 spring.config.import (递归查找)
            // 传参变化：传入 root 和 resDir，以便正确解析 classpath:
            parseSpringImports(root, resDir, mainYmlMap, yaml, allConfigs);
            // 3.2 解析当前文件中的配置
            extractCacheConfigs(mainYmlMap, allConfigs);
        }
        if (allConfigs.isEmpty()) {
            System.err.println("[JMultiCache] 警告: 未在配置文件及其导入文件中找到 'j-multi-cache.configs'");
        } else {
            System.out.println(" - 扫描到配置项: " + allConfigs.size() + " 个");
        }
        // 4. 提取 Namespace 映射
        Map<String, String> enumToNamespace = new LinkedHashMap<>();
        for (String key : new TreeSet<>(allConfigs.keySet())) {
            Object configObj = allConfigs.get(key);
            if (configObj instanceof Map) {
                Map<?, ?> cfg = (Map<?, ?>) configObj;
                Object namespaceObj = cfg.get("namespace");
                String namespace = (namespaceObj != null) ? namespaceObj.toString() : key;
                enumToNamespace.put(key, namespace);
            }
        }
        // 5. 生成并写入文件
        String sourceCode = generateEnumSource(enumToNamespace, pkg, clzName);
        File dir = Paths.get(root, outDir).toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + dir.getAbsolutePath());
        }
        File file = new File(dir, clzName + ".java");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            writer.write(sourceCode);
        }
        System.out.println("====== [JMultiCache] 生成成功: " + file.getAbsolutePath() + " ======");
    }

    /**
     * 辅助方法：解析 spring.config.import
     * 支持 classpath: 前缀，将其替换为 resourcesDir 路径
     */
    @SuppressWarnings("unchecked")
    private void parseSpringImports(String root, String resDir, Map<String, Object> mainYmlMap, Yaml yaml, Map<String, Object> allConfigs) {
        try {
            Object springObj = mainYmlMap.get("spring");
            if (!(springObj instanceof Map)) return;
            Object configObj = ((Map<?, ?>) springObj).get("config");
            if (!(configObj instanceof Map)) return;
            Object importObj = ((Map<?, ?>) configObj).get("import");

            List<String> importFiles;
            if (importObj instanceof List) {
                importFiles = (List<String>) importObj;
            } else if (importObj instanceof String) {
                importFiles = Collections.singletonList((String) importObj);
            } else {
                return;
            }

            for (String importFile : importFiles) {
                File subFile = null;
                // 处理 classpath: 前缀 -> 替换为用户指定的资源目录
                if (importFile.startsWith("classpath:")) {
                    // e.g. classpath:config/cache.yml -> src/main/resources/config/cache.yml
                    String relPath = importFile.replace("classpath:", "");
                    // 确保路径拼接正确（处理开头斜杠）
                    if (relPath.startsWith("/")) relPath = relPath.substring(1);

                    subFile = Paths.get(root, resDir, relPath).toFile();
                } else {
                    // 相对路径，相对于配置文件所在目录（简化处理，暂认为相对于 root）
                    subFile = Paths.get(root, importFile).toFile();
                }

                if (subFile != null && subFile.exists()) {
                    System.out.println(" - 解析导入文件: " + subFile.getName());
                    try (InputStream subFis = Files.newInputStream(subFile.toPath())) {
                        Map<String, Object> subYml = yaml.load(subFis);
                        extractCacheConfigs(subYml, allConfigs);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[JMultiCache] 解析 imports 失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void extractCacheConfigs(Map<String, Object> ymlMap, Map<String, Object> resultCollector) {
        if (ymlMap == null) return;
        Object rootObj = ymlMap.get("j-multi-cache");
        if (rootObj instanceof Map) {
            Object configsObj = ((Map<?, ?>) rootObj).get("configs");
            if (configsObj instanceof Map) {
                resultCollector.putAll((Map<String, Object>) configsObj);
            }
        }
    }

    private String generateEnumSource(Map<String, String> enumToNamespaceMap,
                                      String packageName,
                                      String className) {

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import lombok.Getter;\n");
        sb.append("import lombok.AllArgsConstructor;\n");
        sb.append("import io.github.vevoly.jmulticache.api.JMultiCacheConfigName;\n\n");
        sb.append("/**\n");
        sb.append(" * 多级缓存配置的【名称索引】。\n");
        sb.append(" * !!! 此文件由 JMultiCacheEnumGenerator 自动生成，请不要手动修改 !!!\n");
        sb.append(" * !!! 修改配置请更新 YML 文件后重新生成 !!!\n");
        sb.append(" * ");
        sb.append("@author vevoly\n");
        sb.append(" */\n\n");
        sb.append("@Getter\n");
        sb.append("@AllArgsConstructor\n");
        sb.append("public enum ").append(className).append(" implements JMultiCacheConfigName {\n\n");

        List<String> enumLines = new ArrayList<>();
        for (Map.Entry<String, String> e : enumToNamespaceMap.entrySet()) {
            enumLines.add(String.format("    %s(\"%s\")", e.getKey(), e.getValue()));
        }
        sb.append(String.join(",\n", enumLines)).append(";\n\n");
        sb.append("    public final String namespace;\n");
        sb.append("}\n");

        return sb.toString();
    }

}