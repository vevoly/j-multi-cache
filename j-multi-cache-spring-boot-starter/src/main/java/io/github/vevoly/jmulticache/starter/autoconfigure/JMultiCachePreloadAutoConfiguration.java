package io.github.vevoly.jmulticache.starter.autoconfigure;

import io.github.vevoly.jmulticache.api.JMultiCachePreload;
import io.github.vevoly.jmulticache.api.annotation.JMultiCachePreloadable;
import io.github.vevoly.jmulticache.core.processor.JMultiCachePreloadProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 緩存預熱自動裝配器
 * @Author： Karami
 *
 * 启动时扫描所有带 @CachePreloadable 注解的 Bean，
 * 自动调用 {@link JMultiCachePreloadProcessor} 执行预热逻辑。
 */
@Slf4j
public class JMultiCachePreloadAutoConfiguration implements CommandLineRunner {
    private final Executor asyncExecutor;
    private final ApplicationContext applicationContext;
    private final List<JMultiCachePreload> preloadServices;
    private final JMultiCachePreloadProcessor cachePreloadProcessor;

    private static final boolean ASYNC = true;
    private static final boolean FAIL_FAST = false;
    private static final String LOG_PREFIX = "[JMultiCache Preload] ";

    public JMultiCachePreloadAutoConfiguration(
            List<JMultiCachePreload> preloadServices,
            @Qualifier("jMultiCacheAsyncExecutor") Executor asyncExecutor,
            ApplicationContext applicationContext,
            JMultiCachePreloadProcessor cachePreloadProcessor
    ) {
        this.preloadServices = preloadServices;
        this.asyncExecutor = asyncExecutor;
        this.applicationContext = applicationContext;
        this.cachePreloadProcessor = cachePreloadProcessor;
    }

    @Override
    public void run(String... args) {

        log.info(LOG_PREFIX + " ====== 开始执行应用启动缓存预热 ====== (async = {}, failFast = {})", ASYNC, FAIL_FAST);

        StopWatch stopWatch = new StopWatch("Total Cache Preload");
        stopWatch.start();

        // 1 执行注解模式的预热
        List<Runnable> annotationTasks = buildAnnotationPreloadTasks();

        // 2 执行接口模式的预热
        List<Runnable> interfaceTasks = buildInterfacePreloadTasks();

        // 合并任务
        List<Runnable> allTasks = new ArrayList<>();
        allTasks.addAll(annotationTasks);
        allTasks.addAll(interfaceTasks);

        if (allTasks.isEmpty()) {
            log.info(LOG_PREFIX + "没有发现任何可预热的服务，跳过执行。");
            return;
        }

        // 执行任务
        if (ASYNC) {
            runAsync(allTasks);
        } else {
            runSync(allTasks);
        }

        stopWatch.stop();
        log.info(LOG_PREFIX + " ====== 所有缓存预热任务执行完毕，总耗时: {} ms ======",
                stopWatch.getTotalTimeMillis());
    }

    /**
     * 扫描所有带有 @CachePreload 的 Bean 并构建任务列表
     */
    private List<Runnable> buildAnnotationPreloadTasks() {
        List<Runnable> tasks = new ArrayList<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(JMultiCachePreloadable.class);
        log.info(LOG_PREFIX + "检测到 {} 个带 @MultiCachePreload 的 Bean", beans.size());
        if (beans.isEmpty()) return tasks;

        for (Object bean : beans.values()) {

			/*
			获取真正的目标类（去掉 CGLIB / JDK 代理壳）
			Spring 在加载带有注解的 Bean 时，如果该 Bean 使用了：@Transactional，@Service，@Async 或其他 AOP 增强
			Spring 会生成一个 代理类，名字如： TenantServiceImpl$$SpringCGLIB$$0
			注解在原始类上，而代理类本身没有任何注解，就会造成拿不到注解的后果，所以要用 AopUtils.getTargetClass(bean) 拿到真正的原始类
			 */
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            JMultiCachePreloadable anno = targetClass.getAnnotation(JMultiCachePreloadable.class);
            if (Objects.isNull(anno)) {
                log.warn(LOG_PREFIX + "Bean {} 未找到 MultiCachePreload 注解，跳过", bean.getClass().getSimpleName());
                continue;
            }
            tasks.add(() -> cachePreloadProcessor.process(bean, anno));
        }
        return tasks;
    }

    /**
     * 接口模式
     * @return
     */
    private List<Runnable> buildInterfacePreloadTasks() {
        log.info(LOG_PREFIX + "检测到 {} 个实现 JMultiCachePreload 的 Bean", preloadServices.size());
        List<Runnable> tasks = new ArrayList<>();
        if (preloadServices == null) return tasks;
        for (int i = 0; i < preloadServices.size(); i++) {
            int index = i;
            JMultiCachePreload s = preloadServices.get(i);
            tasks.add(() -> runSingleTask(index, s));
        }
        return tasks;
    }

    /**
     * 串行执行
     */
    private void runSync(List<Runnable> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).run();
        }
    }

    /**
     * 并行执行
     */
    private void runAsync(List<Runnable> tasks) {
        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(task, asyncExecutor))
                .toList();

        if (FAIL_FAST) {
            futures.forEach(CompletableFuture::join);
        } else {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * 执行单个任务
     */
    private void runSingleTask(int index, JMultiCachePreload service) {
        String serviceName = service.getPreloadName();
        try {
            StopWatch taskStopWatch = new StopWatch();
            taskStopWatch.start();
            int count = service.preloadMultiCache();
            taskStopWatch.stop();
            log.info(LOG_PREFIX + " {}. {}: {} 条数据 (耗时: {} ms)",
                    index + 1, serviceName, count, taskStopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            log.error(LOG_PREFIX + " [ERROR] {}. {} 预热失败!", index + 1, serviceName, e);
            if (FAIL_FAST) {
                throw e;
            }
		}
	}
}
