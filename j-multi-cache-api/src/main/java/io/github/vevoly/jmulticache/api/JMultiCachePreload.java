package io.github.vevoly.jmulticache.api;

/**
 * 缓存预热接口
 */
public interface JMultiCachePreload {

    /**
     * 执行缓存预热操作。
     * @return 成功预热的缓存条目数量。
     */
    int preloadMultiCache();

    /**
     * 提供一个名字，用于在日志中清晰地标识。
     */
    default String getPreloadName() {

        // 默认返回类名，去掉 "Service" 和 "Impl" 后缀
        return this.getClass().getSimpleName()
                .replace("ServiceImpl", "")
                .replace("Service", "");
    }
}
