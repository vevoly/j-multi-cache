package io.github.vevoly.jmulticache.core.config;

/**
 * 这是一个标记 Bean，用于保存 @EnableJMultiCache 注解中的配置信息。
 * 它的存在同时也标志着用户启用了该框架。
 * @author vevoly
 */
public class JMultiCacheMarkerConfiguration {

    private boolean preload;

    public boolean isPreload() {
        return preload;
    }

    public void setPreload(boolean preload) {
        this.preload = preload;
    }
}
