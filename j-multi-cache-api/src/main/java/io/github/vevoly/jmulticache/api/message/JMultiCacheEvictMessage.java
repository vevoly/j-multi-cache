package io.github.vevoly.jmulticache.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 失效缓存消息
 *
 * @author vevoly
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JMultiCacheEvictMessage implements Serializable {

    /**
     * 需要失效的缓存名
     */
    private String cacheName;

    /**
     * 需要失效的缓存key
     */
    private String fullKey;
}
