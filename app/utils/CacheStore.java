package utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import models.Project;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheStore
 */
public class CacheStore {
    public static Map<String, Long> sessionMap = new ConcurrentHashMap<>();
    public static Map<String, Long> projectMap = new ConcurrentHashMap<>();
    public static final int MAXIMUM_CACHED_MARKDOWN_ENTRY = 10000;

    /**
     * Introduced to using LRU Cache. It depends on google Guava.
     * <p>
     * Size expectation: 500 char-per-item * 3 byte * 10000 rendered-entry % 70 gzipped = ~10Mb
     */
    public static Cache<Integer, byte[]> renderedMarkdown = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHED_MARKDOWN_ENTRY)
            .build();


    public static void refreshProjectMap(){
        for (Map.Entry<String, Long> entry: projectMap.entrySet()) {
            String[] keys = entry.getKey().split(":");
            Project project= Project.find.where().ieq("owner", keys[0]).ieq("name", keys[1])
                    .findUnique();
            if(project == null){
                projectMap.remove(entry.getKey());
            }
        }
    }
}
