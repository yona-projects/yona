package utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import models.Project;
import models.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static utils.HttpUtil.decodeUrlString;

/**
 * CacheStore
 */
public class CacheStore {
    public static Map<String, Long> projectMap = new ConcurrentHashMap<>();
    public static final int MAXIMUM_CACHED_MARKDOWN_ENTRY = 10000;
    public static final int MAXIMUM_CACHED_YONA_USER_ENTRY = 2000;

    /**
     * Introduced to using LRU Cache. It depends on google Guava.
     * <p>
     * Size expectation: 500 char-per-item * 3 byte * 10000 rendered-entry % 70 gzipped = ~10Mb
     */
    public static Cache<Integer, byte[]> renderedMarkdown = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHED_MARKDOWN_ENTRY)
            .build();

    public static Cache<Long, User> yonaUsers = CacheBuilder.newBuilder()
            .maximumSize(MAXIMUM_CACHED_YONA_USER_ENTRY)
            .build();


    public static String getProjectCacheKey(String owner, String projectName) {
        return decodeUrlString(owner) + ":" + decodeUrlString(projectName);
    }

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
