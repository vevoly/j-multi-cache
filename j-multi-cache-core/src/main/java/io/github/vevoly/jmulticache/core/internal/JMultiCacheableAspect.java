package io.github.vevoly.jmulticache.core.internal;

import io.github.vevoly.jmulticache.api.annotation.JMultiCacheable;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import io.github.vevoly.jmulticache.core.config.JMultiCacheConfigResolver;
import io.github.vevoly.jmulticache.core.utils.I18nLogger;
import io.github.vevoly.jmulticache.core.utils.JMultiCacheInternalHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 处理 {@link JMultiCacheable} 注解的 AOP 切面。
 * <p>
 * 负责拦截目标方法，根据注解配置自动进行单点或批量缓存的读写操作。
 * <p>
 * Aspect for handling the {@link JMultiCacheable} annotation.
 * Intercepts target methods and automatically performs single or batch cache operations based on the annotation configuration.
 *
 * @author vevoly
 */
@Slf4j
@Aspect
@Component
public class JMultiCacheableAspect {

    private final JMultiCacheImpl jMultiCacheManager;
    private final JMultiCacheConfigResolver configResolver;
    private final I18nLogger i18nLogger = new I18nLogger(log);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    // 缓存反射获取的注解信息，避免多次查找 / Cache reflection info to avoid repeated lookups
    private static final Map<Method, JMultiCacheable> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    public JMultiCacheableAspect(JMultiCacheImpl jMultiCacheManager, JMultiCacheConfigResolver configResolver) {
        this.jMultiCacheManager = jMultiCacheManager;
        this.configResolver = configResolver;
    }

    @Around("@annotation(jMultiCacheable)")
    public Object around(ProceedingJoinPoint joinPoint, JMultiCacheable jMultiCacheable) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 确保获取的是当前执行的注解实例（处理继承等情况）
        JMultiCacheable annotation = getAnnotation(method);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        // 1. 获取缓存配置 / Resolve Cache Configuration
        String configName = annotation.configName();

        // 如果注解中未指定，则根据类名自动推断 / Infer from class name if not specified
        if (!StringUtils.hasText(configName)) {
            Object target = joinPoint.getTarget();
            configName = JMultiCacheInternalHelper.inferConfigNameFromClass(target);
            if (!StringUtils.hasText(configName)) {
                i18nLogger.warn("aop.config_infer_failed", target.getClass().getSimpleName(), method.getName());
                return joinPoint.proceed();
            }
        }

        ResolvedJMultiCacheConfig config = configResolver.resolve(configName);

        // 2. 强制刷新处理 / Handle Force Refresh
        if (annotation.forceRefresh()) {
            i18nLogger.info("aop.force_refresh", method.getName());
            return joinPoint.proceed();
        }

        // 3. 判断是单点查询还是批量查询 / Determine Single or Batch Query
        boolean isBatchQuery = JMultiCacheInternalHelper.isCollectionParam(joinPoint);

        if (isBatchQuery) {
            return handleBatchQuery(joinPoint, config);
        } else {
            return handleSingleQuery(joinPoint, config);
        }
    }

    /**
     * 处理单点查询逻辑。
     */
    private Object handleSingleQuery(ProceedingJoinPoint joinPoint, ResolvedJMultiCacheConfig config) {
        // 从方法参数中解析 Key 的各部分值 (支持 SpEL)
        String[] keyPart = JMultiCacheInternalHelper.getKeyValuesFromMethodArgs(joinPoint, config.getKeyField());
        // 定义回源加载器
        Supplier<Object> dbLoader = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
        // 调用 Manager 的 AOP 专用方法
        return jMultiCacheManager.fetchDataForAop(config, dbLoader, keyPart);
    }

    /**
     * 处理批量查询逻辑。
     */
    @SuppressWarnings("unchecked")
    private Object handleBatchQuery(ProceedingJoinPoint joinPoint, ResolvedJMultiCacheConfig config) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] originalArgs = joinPoint.getArgs();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());

        if (paramNames == null || originalArgs.length == 0) {
            // 无法获取参数名或无参数，无法执行批量逻辑
            try { return joinPoint.proceed(); } catch (Throwable e) { throw new RuntimeException(e); }
        }
        // 提取 ID 列表 (约定：第一个参数必须是 Collection)
        Collection<Object> idsToQuery = (Collection<Object>) originalArgs[0];
        if (CollectionUtils.isEmpty(idsToQuery)) {
            Class<?> returnType = signature.getReturnType();
            return Map.class.isAssignableFrom(returnType) ? Collections.emptyMap() : Collections.emptyList();
        }

        // 推断 businessKey (根据约定：将参数名转为单数形式，例如 userIds -> userId)
        String businessKey = JMultiCacheInternalHelper.toSingleForm(paramNames[0]);

        // 1. 构建 DB 回源函数
        Function<Collection<Object>, Object> dbFetcher = (missingIds) -> {
            try {
                // 创建一个新的参数数组，将第一个参数替换为 missingIds
                Object[] newArgs = new Object[originalArgs.length];
                newArgs[0] = new ArrayList<>(missingIds); // 确保是具体 List 类型
                System.arraycopy(originalArgs, 1, newArgs, 1, originalArgs.length - 1);

                // 调用原始方法
                return joinPoint.proceed(newArgs);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        // 2. 构建动态 KeyBuilder (支持 SpEL)
        // 这个 builder 的上下文将包含当前处理的 id 以及方法的所有原始参数
        Function<Object, String> keyBuilder = (currentItemId) -> {
            StandardEvaluationContext context = new StandardEvaluationContext();
            // A. 将所有非 ID 列表的参数，按原名放入上下文 (p1, p2...)
            for (int i = 1; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], originalArgs[i]);
            }
            // B. 智能适配的核心：将当前遍历到的 ID，以单数形式的变量名放入上下文
            //【约定】： SpEL 中代表“批量ID”的变量名，与方法中 ID 列表参数的单数形式一致。例如：方法参数 List<Long> userIds -> SpEL 变量 #userId
            context.setVariable(businessKey, currentItemId);
            // C. 执行 SpEL 求值
            // 注意：这里解析的是 YML 中的 keyField 表达式 (例如 "#userId + ':' + #type")
            Object keyPartResult = parser.parseExpression(config.getKeyField()).getValue(context);
            String keyPart = String.valueOf(keyPartResult);
            // D. 拼接成完整的缓存 Key
            return JMultiCacheHelper.buildKey(config.getNamespace(), keyPart);
        };
        // 调用 Manager 的 AOP 专用批量方法
        return jMultiCacheManager.fetchMultiDataForAop(config, idsToQuery, businessKey, keyBuilder, dbFetcher);
    }

    private JMultiCacheable getAnnotation(Method method) {
        return ANNOTATION_CACHE.computeIfAbsent(method, m -> AnnotationUtils.findAnnotation(m, JMultiCacheable.class));
	}

}
