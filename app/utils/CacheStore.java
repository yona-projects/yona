package utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import models.Project;
import models.User;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheStore
 */
public class CacheStore {
    public static Map<String, User> sessionMap = new ConcurrentHashMap<>();
    public static Map<String, Project> projectMap = new ConcurrentHashMap<>();
    public static final int MAXIMUM_CACHED_MARKDOWN_ENTRY = 10000;

    /**
     * Introduced to using LRU Cache. It depends on google Guava.
     * <p>
     * Size expectation: 500 char-per-item * 3 byte * 10000 rendered-entry % 70 gzipped = ~10Mb
     */
    public static LoadingCache<Integer, byte[]> renderedMarkdown = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHED_MARKDOWN_ENTRY)
            .build(
                    new CacheLoader<Integer, byte[]>() {
                        public byte[] load(@Nonnull Integer key) {
                            return null;
                        }
                    });
}
