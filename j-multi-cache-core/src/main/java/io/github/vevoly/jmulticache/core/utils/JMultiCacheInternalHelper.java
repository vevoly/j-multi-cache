package io.github.vevoly.jmulticache.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.constants.DefaultStorageTypes;
import io.github.vevoly.jmulticache.api.constants.JMultiCacheConstants;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;
import org.apache.commons.collections4.CollectionUtils;
import com.google.common.base.CaseFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * j-multi-cache 框架的内部辅助工具类。
 * <p>
 * 【警告】此类及其方法与框架的内部实现紧密耦合，不属于公共 API 的一部分，未来版本可能随时更改。
 * 请勿在您的业务代码中直接调用此类。
 * <p>
 * [WARNING] This class and its methods are tightly coupled with the framework's internal implementation,
 * are not part of the public API, and may change at any time in future releases.
 * Do not call this class directly in your business code.
 *
 * @author vevoly
 */
@Slf4j
public class JMultiCacheInternalHelper {

    private JMultiCacheInternalHelper() {}

    private static final String LOG_PREFIX = "[JMultiCache-Helper] ";
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    // 用于获取 AOP 切点方法的参数名
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    // ===================================================================
    // ====================== 空值处理 / Null Value Handling ==============
    // ===================================================================

    /**
     * 将一个可能是空值标记的缓存命中结果，转换回真正的 null 或空集合/Map，以返回给调用者。
     * <p>
     * Converts a cache hit result that might be a null value marker back to a true null or an empty collection/map to be returned to the caller.
     *
     * @param result 缓存中获取的结果。/ The result obtained from the cache.
     * @param config 当前操作的配置。/ The configuration for the current operation.
     * @param <T>    结果的泛型类型。/ The generic type of the result.
     * @return 净化后的结果。/ The sanitized result.
     */
    public static <T> T handleCacheHit(T result, ResolvedJMultiCacheConfig config) {
        return JMultiCacheHelper.isSpecialEmptyData(result, null) ? JMultiCacheHelper.specialEmptyData2EmptyData(result, config) : result;
    }

    /**
     * 根据类型信息和配置，创建一个用于防止缓存穿透的空值对象。
     * <p>
     * Creates a null-value object for preventing cache penetration, based on type information and configuration.
     *
     * @param typeRef 目标类型的 TypeReference。/ The TypeReference of the target type.
     * @param config  当前操作的配置。/ The configuration for the current operation.
     * @param <T>     结果的泛型类型。/ The generic type of the result.
     * @return 代表空值的对象 (可能是字符串、单元素集合或单元素 Map)。/ An object representing the null value (could be a string, a singleton collection, or a singleton map).
     */
    public static <T> T createEmptyData(TypeReference<T> typeRef, ResolvedJMultiCacheConfig config) {
        Class<?> rawClass = typeRef.getType().getClass();
        String emptyValueMark = config.getEmptyValueMark();

        if (List.class.isAssignableFrom(rawClass)) {
            return (T) Collections.singletonList(emptyValueMark);
        }
        if (Set.class.isAssignableFrom(rawClass)) {
            return (T) Collections.singleton(emptyValueMark);
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            return (T) Collections.singletonMap(emptyValueMark, Boolean.TRUE);
        }
        return (T) emptyValueMark;
    }

    // ===================================================================
    // ====================== Key 解析 / Key Parsing =======================
    // ===================================================================

    /**
     * 将一个 ID 集合转换为一个稳定的 MD5 哈希值。
     * <p>
     * 此方法通过对 ID 进行排序和拼接，确保对于相同的 ID 集合（无论顺序如何），总是生成相同的 MD5 值。
     * 主要用于为批量操作创建统一的分布式锁键。
     * <p>
     * Converts a collection of IDs into a stable MD5 hash.
     * By sorting and joining the IDs, this method ensures that the same set of IDs (regardless of order) always produces the same MD5 hash.
     * This is primarily used to create a uniform distributed lock key for batch operations.
     *
     * @param ids ID 的集合。/ A collection of IDs.
     * @return 32位的 MD5 哈希字符串。/ A 32-character MD5 hash string.
     */
    public static String getMd5Key(Collection<?> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return "";
        }
        String sortedKeysString = ids.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(","));

        // 使用 Apache Commons Codec 来生成 MD5
        return DigestUtils.md5Hex(sortedKeysString);
    }

    /**
     * 安全地从对象中根据表达式提取 key，支持 SpEL 和反射兜底。
     * <p>
     * Safely extracts a key from an object based on an expression, supporting SpEL with a reflection-based fallback.
     */
    public static String getKeyValueSafe(Object obj, String keyExpr) {
        try {
            if (!StringUtils.hasText(keyExpr)) return "global";
            if (obj == null) return null;


            // 1. 对 Map 类型的特殊处理 (高优先级)
            if (obj instanceof Map && !keyExpr.contains("#")) {
                Object value = ((Map<?, ?>) obj).get(keyExpr);
                return (value == null) ? null : String.valueOf(value);
            }
            // 2. 简单类型处理
            if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj.getClass().isPrimitive()) {
                return String.valueOf(obj);
            }
            // 3. 固定值 "null" 常量处理
            if ("null".equalsIgnoreCase(keyExpr)) return "null";
            // 4.智能 SpEL 上下文，同时支持带 # 和不带 #
            Object value = null;
            boolean spelAttempted = false;
            try {
                // 创建一个同时支持“根对象”和“变量”的混合上下文
                StandardEvaluationContext context = new StandardEvaluationContext(obj);
                registerAllFieldsAsVariables(context, obj);
                value = PARSER.parseExpression(keyExpr).getValue(context);
                spelAttempted = true;
            } catch (ParseException | EvaluationException e) {
                // 忽略这个异常，进入下面的兜底逻辑 / Ignore benign exceptions, allowing fallback
            } catch (Exception e) {
                log.warn(LOG_PREFIX + "Unexpected error while executing SpEL expression '{}': {}", keyExpr, e.getMessage());
            }
            // 5. 兜底逻辑：当 SpEL 未尝试(理论上不会)，或 SpEL 结果无效时
            if (!spelAttempted || isInvalidSpelResult(String.valueOf(value))) {
                // 将表达式视为一个简单的字段名
                String fieldName = keyExpr.startsWith("#") ? keyExpr.substring(1) : keyExpr;
                // 复合表达式如 "tenantId + countryCode" 在这里必然失败，这是符合预期的。
                if (!fieldName.matches(".*[+\\-*/\\[\\].()\\s].*")) {
                    value = tryGetByGetterOrField(obj, fieldName);
                }
            }
            // 6. 最终结果处理
            if (value != null && !isInvalidSpelResult(String.valueOf(value))) {
                return String.valueOf(value);
            }
            log.warn(LOG_PREFIX + "Could not extract key '{}' from object {} (tried SpEL and reflection fallback)",
                    keyExpr, obj.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Error parsing key expression '{}': {}", keyExpr, e.getMessage());
            return null;
        }
    }

    /**
     * 检查一个字符串是否是 SpEL 拼接 null 产生的无效结果 (例如 "null", "null:null")。
     * <p>
     * Checks if a string is an invalid result from SpEL concatenating nulls (e.g., "null", "null:null").
     */
    private static boolean isInvalidSpelResult(String resultStr) {
        if (resultStr == null) return true;
        return resultStr.matches("^(null[:]?)+$");
    }

    /**
     * 通过反射将一个对象的所有字段注册到 SpEL 上下文中作为变量，以支持 '#fieldName' 风格的表达式。
     * <p>
     * Registers all fields of an object as variables in the SpEL context via reflection to support '#fieldName' style expressions.
     */
    private static void registerAllFieldsAsVariables(StandardEvaluationContext context, Object obj) {
        if (obj == null) return;
        // 遍历对象的所有字段（包括父类）
        for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);  // 允许访问私有字段
                    // 将字段名作为变量名，字段值作为变量值
                    context.setVariable(field.getName(), field.get(obj));
                } catch (IllegalAccessException e) {
                    // 忽略无法访问的字段 / Ignore inaccessible fields
                }
            }
        }
    }

    /**
     * 优先通过 getter 获取字段值，失败后回退到直接反射字段。
     * <p>
     * Tries to get a field value via its getter method, falling back to direct field reflection on failure.
     */
    private static Object tryGetByGetterOrField(Object obj, String fieldName) {
        if (obj == null || !StringUtils.hasText(fieldName)) return null;
        // 1. 获取真正的、非代理的目标类
        Class<?> targetClass = AopUtils.getTargetClass(obj);
        // 2. 尝试调用标准的 getter: getXxx()
        try {
            // 使用 getMethod，它可以查找到父类的 public 方法
            Method getter = targetClass.getMethod("get" + StringUtils.capitalize(fieldName));
            return getter.invoke(obj);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Failed to invoke getter for '{}': {}", fieldName, e.getMessage());
        }
        // 3. 尝试调用布尔型的 getter: isXxx()
        try {
            Method getter = targetClass.getMethod("is" + StringUtils.capitalize(fieldName));
            return getter.invoke(obj);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Failed to invoke getter for '{}': {}", fieldName, e.getMessage());
        }
        // 4. 如果所有 getter 都失败，递归查找并访问字段
        try {
            Field field = findField(targetClass, fieldName); // 递归查找字段
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Failed to access field '{}' directly: {}", fieldName, e.getMessage());
        }
        // 5. 如果所有方式都失败，则返回 null
        return null;
    }

    /**
     * 在类及其父类中递归向上查找一个字段。
     * <p>
     * Recursively searches for a field in a class and its superclasses.
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    // ===================================================================
    // ====================== AOP 相关 / AOP Related =======================
    // ===================================================================

    /**
     * 判断 AOP 切点方法的第一个参数是否为 Collection 类型，以此来区分批量查询模式。
     * <p>
     * Checks if the first parameter of an AOP join point's method is of type Collection to distinguish batch query mode.
     *
     * @param joinPoint AOP 切点。/ The AOP join point.
     * @return {@code true} 如果第一个参数是 Collection。/ {@code true} if the first parameter is a Collection.
     */
    public static boolean isCollectionParam(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return false;
        }
        return args[0] instanceof Collection;
    }

    /**
     * 从方法参数中解析多个 key 值（支持 SpEL 表达式中包含多个变量）
     */
    public static String[] getKeyValuesFromMethodArgs(ProceedingJoinPoint joinPoint, String keyExpr) {
        if (keyExpr.isBlank()) {
            return new String[]{String.valueOf(joinPoint.getArgs()[0])};
        }

        // 支持多变量形式，如 "#tenantId + ':' + #platformCode + ':' + #code"
        String resolved = getKeyValueFromMethodArgs(joinPoint, keyExpr);
        return resolved.split(":");
    }

    /**
     * 从 AOP 切点的方法参数中动态解析 key 的值。
     * <p>
     * Dynamically resolves the key's value from the method arguments of an AOP join point.
     *
     * @param joinPoint AOP 切点。/ The AOP join point.
     * @param keyExpr   要解析的 SpEL 表达式。/ The SpEL expression to parse.
     * @return 解析后的 key 字符串。/ The resolved key string.
     */
    public static String getKeyValueFromMethodArgs(ProceedingJoinPoint joinPoint, String keyExpr) {
        try {
            Object[] args = joinPoint.getArgs();
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);

            // 没配置 key，默认取第一个参数或 "global"
            if (!StringUtils.hasText(keyExpr)) {
                return (args.length == 0) ? "global" : String.valueOf(args[0]);
            }

            // 构建 SpEL 上下文
            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            // 调用公共逻辑处理
            return getKeyValueInternal(keyExpr, context, args);
        } catch (Exception e) {
            log.warn(LOG_PREFIX + "Could not resolve cache key from method arguments: {}. Falling back to 'error_key'.", e.getMessage());
            return "error_key";
        }
    }

    /**
     * 从配置对象中获取缓存 key（适用于业务层手动拼接）
     * @param config 缓存配置
     * @param keyValue 多个 key 值（例如 tenantId, platformCode, code）
     */
    public static String getCacheKeyFromConfig(ResolvedJMultiCacheConfig config, String... keyValue) {
        String keyField = JMultiCacheConstants.DEFAULT_KEY_FIELD;
        if (config != null && org.apache.commons.lang3.StringUtils.isNotBlank(config.getKeyField())) {
            keyField = config.getKeyField();
        }
        return JMultiCacheHelper.buildKey(config.getNamespace(), getKeyValue(keyField, keyValue));
    }

    public static String toSingleForm(String pluralName) {
        if (pluralName.endsWith("s")) {
            return pluralName.substring(0, pluralName.length() - 1);
        }
        if (pluralName.endsWith("List")) {
            return pluralName.substring(0, pluralName.length() - 4);
        }
        // todo 可以增加更复杂的单复数转换规则
        return pluralName;
    }

    /**
     * 核心通用方法：根据 keyField 表达式和 key 值数组，拼接最终缓存 key
     *  - 支持 SpEL 表达式
     *  - 支持常量、拼接、多字段形式
     *  - 统一容错与空值处理
     */
    public static String getKeyValue(String keyField, String... keyValue) {
        try {

            // 如果为空则返回第一个 key 或 "global"
            if (org.apache.commons.lang3.StringUtils.isBlank(keyField)) {
                return (keyValue == null || keyValue.length == 0) ? "global" : String.join(":", keyValue);
            }

            // 非 SpEL 直接走普通拼接（兼容原有行为）
            if (!keyField.contains("#")) {
                return buildPlainKey(keyField, keyValue);
            }

            // 构造 EvaluationContext 并绑定 p0,p1...
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (keyValue != null) {

                // **增强绑定**：把表达式里出现的变量名（#tenantId、#countryCode）按出现顺序与 keyValue 对应绑定
                List<String> varNames = extractSpELFieldNames(keyField);
                for (int i = 0; i < varNames.size() && i < keyValue.length; i++) {
                    // 只有当变量名尚未绑定时，才进行绑定（避免覆盖 pN）
                    if (context.lookupVariable(varNames.get(i)) == null) {
                        context.setVariable(varNames.get(i), keyValue[i]);
                    }
                }
            }

            // 解析表达式（如果 keyField 以 # 开头，去掉首个 #，一致处理）
            Object value = PARSER.parseExpression(keyField).getValue(context);
            return value == null ? "null" : String.valueOf(value);

        } catch (Exception e) {
            log.warn(LOG_PREFIX + "无法从配置拼接 key，keyField={}, err={}", keyField, e.getMessage());
            return "error";
        }
    }

    /**
     * 拼接普通 key 值（非 SpEL）
     * 支持 keyField 占位符写法，如 "tenantId:platform:code"
     */
    private static String buildPlainKey(String keyField, String... keyValue) {
        if (keyValue == null || keyValue.length == 0) {
            return keyField;
        }

        // 如果 keyField 仅是模板或单字段名，直接拼 keyValue
        if (!keyField.contains(":")) {
            return String.join(":", keyValue);
        }
        return keyField + ":" + String.join(":", keyValue);
    }

    /**
     * 简单提取 SpEL 表达式中的变量名，如 "#tenantId + ':' + #countryCode"
     */
    private static List<String> extractSpELFieldNames(String keyField) {
        Matcher matcher = Pattern.compile("#([a-zA-Z0-9_]+)").matcher(keyField);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 内部通用逻辑复用（供 getKeyValueFromMethodArgs 使用）
     */
    private static String getKeyValueInternal(String keyExpr, EvaluationContext context, Object[] args) {
        try {
            // 1. SpEL 表达式
            if (keyExpr.startsWith("#")) {
                Object value = PARSER.parseExpression(keyExpr).getValue(context);
                return value == null ? "null" : String.valueOf(value);
            }
            // 2. 字面量常量
            if (keyExpr.startsWith("'") && keyExpr.endsWith("'")) {
                return keyExpr.substring(1, keyExpr.length() - 1);
            }

            // 3. 尝试匹配参数名（非SpEL写法）
            if (context instanceof StandardEvaluationContext) {
                StandardEvaluationContext sec = (StandardEvaluationContext) context;
                try {
                    Field field = StandardEvaluationContext.class.getDeclaredField("variables");
                    field.setAccessible(true);
                    Map<String, Object> variables = (Map<String, Object>) field.get(sec);
                    if (variables != null) {
                        for (String variableName : variables.keySet()) {
                            if (variableName.equals(keyExpr)) {
                                Object value = sec.lookupVariable(variableName);
                                return value == null ? "null" : value.toString();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract variable names from StandardEvaluationContext", e);
                }
            }

            // 4. 尝试在第一个参数对象中找字段 （第一个参数是对象）
            if (args.length > 0 && args[0] != null) {
                Object firstArg = args[0];
                try {
                    Field field = firstArg.getClass().getDeclaredField(keyExpr);
                    field.setAccessible(true);
                    Object val = field.get(firstArg);
                    return val == null ? "null" : String.valueOf(val);
                } catch (NoSuchFieldException ignore) { }
            }

            // 5. 默认直接返回表达式
            return keyExpr;

        } catch (Exception e) {
            log.warn(LOG_PREFIX + "内部解析 keyExpr 出错: {}", e.getMessage());
            return "error";
        }
    }

    // ===================================================================
    // ====================== 数据归一化 / Data Normalization =============
    // ===================================================================

    /**
     * 通用数据库结果归一化方法。
     * <p>
     * 根据 {@code businessKey} 将数据库返回的原始结果 (可能是 List, Set 或 Map) 统一转换为 {@code Map<String, V>}。
     * 它允许用户在批量查询时只返回一个 List，由框架负责根据业务主键将其映射为 Map。
     * <p>
     * Universal database result normalization method.
     * Converts raw results returned by the database (which could be List, Set, or Map) into a unified {@code Map<String, V>} based on the {@code businessKey}.
     * allowing users to return a simple List in batch queries, with the framework responsible for mapping it to a Map based on the business primary key.
     *
     * @param dbRaw       数据库返回的原始数据对象。/ The raw data object returned by the database.
     * @param businessKey 用于提取 Map Key 的业务字段名 (针对 Collection 类型结果必填)。/ The business field name used to extract the Map Key (required for Collection type results).
     * @param <V>         值的类型。/ The type of the value.
     * @return 归一化后的 Map，Key 为 String 类型的 ID (由 businessKey 提取)。/ The normalized Map, where the Key is a String ID (extracted via businessKey).
     */
    @SuppressWarnings("unchecked")
    public static <V> Map<String, V> normalizeDbResultToMap(Object dbRaw, String businessKey) {
        if (dbRaw == null) {
            return Collections.emptyMap();
        }

        // 1. 如果本身就是 Map，直接转换 key 为 String 返回 / 1. If it is already a Map, convert keys to String and return.
        if (dbRaw instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return Collections.emptyMap();
            }
            // 即使是 Map，我们也确保 Key 转为 String，以防用户返回了 Map<Long, Entity>
            // even if it is a Map, we ensure that the Key is converted to String to prevent users from returning Map<Long, Entity>
            return map.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .collect(Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> (V) e.getValue(),
                            (v1, v2) -> v1 // 键冲突时保留前者 / Keep the first one on key collision
                    ));
        }

        // 2. 如果是 Collection，利用 businessKey 进行转换 / If it is a Collection, use businessKey to convert it.
        if (dbRaw instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return Collections.emptyMap();
            }

            if (!StringUtils.hasText(businessKey)) {
                // 如果没有 businessKey，我们无法将 List 转为 Map，抛出异常 / If there is no businessKey, we cannot convert List to Map, throw an exception
                throw new IllegalStateException(LOG_PREFIX + "businessKey cannot be empty when converting List/Set to Map. Please check your query parameters.");
            }

            Map<String, V> result = new LinkedHashMap<>();
            for (Object element : collection) {
                if (element == null) continue;
                try {
                    // 使用 getKeyValueSafe (支持 SpEL 和反射) 提取 Key / Use getKeyValueSafe (supports SpEL and reflection) to extract Key
                    String keyStr = getKeyValueSafe(element, businessKey);
                    if (keyStr != null) {
                        result.put(keyStr, (V) element);
                    }
                } catch (Exception e) {
                    // 记录日志但不中断整个流程，跳过有问题的数据项
                    log.warn(LOG_PREFIX + "Error extracting key '{}' from element: {}. Item skipped.", businessKey, e.getMessage());
                }
            }
            return result;
        }

        // 其他类型无法处理，返回空
        return Collections.emptyMap();
    }

    // ===================================================================
    // ====================== 预热相关 / Preload Related =====================
    // ===================================================================

    /**
     * 为缓存预热准备数据：将源数据列表根据 key 表达式进行分组。
     * <p>
     * Prepares data for cache preloading by grouping a list of source data according to a key expression.
     */
   public static Map<String, Object> groupDataForPreload(List<Object> dataList, ResolvedJMultiCacheConfig config, String keyExpr) {
        if (CollectionUtils.isEmpty(dataList)) {
            return Collections.emptyMap();
        }

        Function<Object, String> keyExtractor = entity ->
                config.getNamespace() + ":" + getKeyValueSafe(entity, keyExpr);

        switch (config.getStorageType()) {
            case DefaultStorageTypes.STRING:
                return dataList.stream().collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
            case DefaultStorageTypes.LIST:
                return new HashMap<>(dataList.stream().collect(Collectors.groupingBy(
                        keyExtractor,
                        Collectors.toList()
                )));
            case DefaultStorageTypes.SET:
                return new HashMap<>(dataList.stream().collect(Collectors.groupingBy(
                        keyExtractor,
                        Collectors.toSet()
                )));
            default:
                throw new IllegalArgumentException("Preloading is not supported for storage type: " + config.getStorageType());
        }
    }

    /**
     * 过滤表达式解析（支持 == 和 !=）。
     * <p>
     * Parses a filter expression (supports == and !=).
     */
    public static boolean matchFilter(Object obj, String expr) {
        if (expr.isBlank()) {
            return true;
        }
        try {

            // 优先支持 &&
            if (expr.contains("&&")) {
                String[] subExprs = expr.split("&&");
                for (String sub : subExprs) {
                    if (!matchSingleCondition(obj, sub.trim())) {
                        return false;
                    }
                }
                return true;
            }

            // 再支持 ||
            if (expr.contains("||")) {
                String[] subExprs = expr.split("\\|\\|");
                for (String sub : subExprs) {
                    if (matchSingleCondition(obj, sub.trim())) {
                        return true;
                    }
                }
                return false;
            }

            // 单条件表达式
            return matchSingleCondition(obj, expr);

        } catch (Exception e) {
            log.warn(LOG_PREFIX + "过滤表达式解析失败: {} -> {}", expr, e.getMessage());
            return true; // 出错时默认不过滤
        }
    }

    /**
     * 单个条件匹配，如 "status == 0" 或 "tenantId != 1"
     */
    private static boolean matchSingleCondition(Object obj, String singleExpr) {
        try {
            String operator = singleExpr.contains("==") ? "==" :
                    (singleExpr.contains("!=") ? "!=" : null);
            if (operator == null) {
                return true;
            }

            String[] parts = singleExpr.split(operator);
            if (parts.length != 2) {
                return true;
            }

            String fieldName = parts[0].trim();
            String expectedValue = parts[1].trim().replaceAll("'", "");

            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object actualValue = f.get(obj);

            if (actualValue == null) {
                return false;
            }

            String actualStr = String.valueOf(actualValue);
            return "==".equals(operator) ? actualStr.equals(expectedValue)
                    : !"==".equals(operator) && !actualStr.equals(expectedValue);

        } catch (Exception e) {
            log.warn(LOG_PREFIX + "无法解析过滤条件: {}", singleExpr, e);
            return true;
        }
    }

    // ===================================================================
    // ====================== 名称推断 / Name Inference =====================
    // ===================================================================

    /**
     * 根据 Bean 的类名，智能推断其对应的缓存配置名称 (configName)。
     * <p>
     * Intelligently infers the corresponding cache configuration name (configName) from the bean's class name.
     */
    public static String inferConfigNameFromClass(Object bean) {
        if (bean == null) return null;

        String className = AopUtils.getTargetClass(bean).getSimpleName();
        String baseName = className;

        if (baseName.endsWith("ServiceImpl")) {
            baseName = baseName.substring(0, baseName.length() - "ServiceImpl".length());
        } else if (baseName.endsWith("Service")) {
            baseName = baseName.substring(0, baseName.length() - "Service".length());
        }

        if (!StringUtils.hasText(baseName)) {
            log.warn(LOG_PREFIX + "Could not infer a valid base name from class '{}'.", className);
            return null;
        }

        return camelToUpperUnderscore(baseName);
    }

    /**
     * 将驼峰命名法 (upperCamel) 字符串转换为大写下划线命名法 (UPPER_UNDERSCORE)。
     * <p>
     * 例如: "TenantAppVersion" -> "TENANT_APP_VERSION"
     * <p>
     * Converts an upperCamel case string to UPPER_UNDERSCORE case.
     * For example: "TenantAppVersion" -> "TENANT_APP_VERSION"
     *
     * @param name 驼峰命名的字符串。/ The upperCamel case string.
     * @return 转换后的字符串。/ The converted string.
     */
    static String camelToUpperUnderscore(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        // 使用 Guava 的实现更健壮 / Using Guava's implementation is more robust.
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
    }
}
