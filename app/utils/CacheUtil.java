package utils;

import play.Play;
import play.cache.SyncCacheApi;

public final class CacheUtil {
    private CacheUtil() {
    }

    public static <T> T get(String key) {
        return cache().<T>get(key).orElse(null);
    }

    public static void set(String key, Object value) {
        cache().set(key, value);
    }

    public static void set(String key, Object value, int expiration) {
        cache().set(key, value, expiration);
    }

    public static void remove(String key) {
        cache().remove(key);
    }

    private static SyncCacheApi cache() {
        return Play.application().injector().instanceOf(SyncCacheApi.class);
    }
}
