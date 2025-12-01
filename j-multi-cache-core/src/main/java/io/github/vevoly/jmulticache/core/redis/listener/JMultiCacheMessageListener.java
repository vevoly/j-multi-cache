package io.github.vevoly.jmulticache.core.redis.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import io.github.vevoly.jmulticache.api.message.JMultiCacheEvictMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * 监听 Redis 广播，执行本地 L1 缓存清理
 * @author vevoly
 */
@Slf4j
@RequiredArgsConstructor
public class JMultiCacheMessageListener implements MessageListener {

    private final @Qualifier("jMultiCacheObjectMapper") ObjectMapper objectMapper;
    private final JMultiCacheOps jMultiCacheOps;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JMultiCacheEvictMessage jMultiCacheEvictMessage = objectMapper.readValue(body, JMultiCacheEvictMessage.class);
            String cacheName = jMultiCacheEvictMessage.getCacheName();
            String fullKey = jMultiCacheEvictMessage.getFullKey();

            log.info("[JMultiCache] 收到广播清除消息: cacheName={}, fullKey={}", cacheName, fullKey);
            jMultiCacheOps.evictL1(cacheName, fullKey);

        } catch (Exception e) {
            log.error("[JMultiCache] 处理广播消息失败", e);
        }
    }
}
