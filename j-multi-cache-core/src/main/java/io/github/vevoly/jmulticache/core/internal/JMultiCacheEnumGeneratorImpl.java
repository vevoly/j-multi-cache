package io.github.vevoly.jmulticache.core.internal;

import io.github.vevoly.jmulticache.api.JMultiCacheEnumGenerator;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

/**
 * 多级缓存枚举生成实现类
 * @author vevoly
 */
public class JMultiCacheEnumGeneratorImpl implements JMultiCacheEnumGenerator {

    private static final String DEFAULT_ROOT = "C:\\";
    private static final String DEFAULT_YML = "src/main/resources/j-multi-cache-config.yml";
    private static final String DEFAULT_OUT = "src/main/java/com/github/vevoly/enums/";
    private static final String DEFAULT_PACKAGE = "com.github.vevoly.enums";
    private static final String DEFAULT_CLASS = "JMultiCacheName";

    @Override
    public void generateEnum() throws IOException {
        generateEnum(DEFAULT_ROOT, DEFAULT_YML, DEFAULT_OUT, DEFAULT_PACKAGE, DEFAULT_CLASS);
    }

    @Override
    public void generateEnum(String projectRootPath, String ymlConfigPath, String outputDir, String packageName, String className) throws IOException {

        System.out.println("====== [JMultiCache] 开始生成 " + className + " ======");

        Yaml yaml = new Yaml();
        Map<String, Object> allConfigs = new LinkedHashMap<>();

        File mainConfigFile = Paths.get(projectRootPath, ymlConfigPath).toFile();
        if (!mainConfigFile.exists()) {
            throw new FileNotFoundException("YML 文件不存在: " + mainConfigFile.getAbsolutePath());
        }

        Map<String, Object> mainYml = yaml.load(new FileInputStream(mainConfigFile));

        // 解析 import（如果存在）
        List<String> importFiles = Optional.ofNullable((Map<String, Object>) mainYml.get("spring"))
                .map(s -> (Map<String, Object>) s.get("config"))
                .map(c -> (List<String>) c.get("import"))
                .orElse(Collections.emptyList());

        if (!importFiles.isEmpty()) {
            for (String importFile : importFiles) {
                if (!importFile.startsWith("classpath:")) {
                    continue;
                }

                String rel = importFile.replace("classpath:", "src/main/resources/");
                File subFile = Paths.get(projectRootPath, rel).toFile();
                if (!subFile.exists()) {
                    continue;
                }

                try (FileInputStream fis = new FileInputStream(subFile)) {
                    Map<String, Object> subYml = yaml.load(fis);
                    Optional.ofNullable((Map<String, Object>) subYml.get("j-multi-cache"))
                            .map(r -> (Map<String, Object>) r.get("configs"))
                            .ifPresent(allConfigs::putAll);
                }
            }
        } else {
            Optional.ofNullable((Map<String, Object>) mainYml.get("j-multi-cache"))
                    .map(r -> (Map<String, Object>) r.get("configs"))
                    .ifPresent(allConfigs::putAll);
        }

        if (allConfigs.isEmpty()) {
            throw new IllegalStateException("YML 配置中未找到 j-multi-cache.configs");
        }

        // 组装 enum 对应 namespace
        Map<String, String> enumToNamespace = new LinkedHashMap<>();
        for (String key : new TreeSet<>(allConfigs.keySet())) {
            Map cfg = (Map) allConfigs.get(key);
            String namespace = cfg != null && cfg.get("namespace") != null ? cfg.get("namespace").toString() : key;
            enumToNamespace.put(key, namespace);
        }

        // 生成代码
        String source = generateEnumSource(enumToNamespace, packageName, className);

        // 写入文件
        File dir = Paths.get(projectRootPath, outputDir).toFile();
        dir.mkdirs();

        File file = new File(dir, className + ".java");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(source);
        }

        System.out.println("====== [JMultiCache] 枚举生成成功: " + file.getAbsolutePath());
    }


    private String generateEnumSource(Map<String, String> enumToNamespaceMap,
                                      String packageName,
                                      String className) {

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import lombok.Getter;\n");
        sb.append("import lombok.AllArgsConstructor;\n");
        sb.append("import io.github.vevoly.jmulticache.api.MultiCacheConfigName;\n\n");

        sb.append("@Getter\n");
        sb.append("@AllArgsConstructor\n");
        sb.append("public enum ").append(className).append(" implements MultiCacheConfigName {\n\n");

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