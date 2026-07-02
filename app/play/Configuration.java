package play;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.Set;

public class Configuration {
    private final Config underlying;

    public Configuration(Config underlying) {
        this.underlying = underlying == null ? ConfigFactory.load() : underlying;
    }

    public static Configuration root() {
        return Play.configuration();
    }

    public Config underlying() {
        return underlying;
    }

    public Configuration withFallback(Configuration fallback) {
        if (fallback == null) {
            return this;
        }
        return new Configuration(underlying.withFallback(fallback.underlying()));
    }

    public Configuration getConfig(String path) {
        if (!hasPath(path)) {
            return null;
        }
        return new Configuration(underlying.getConfig(path));
    }

    public Set<String> keys() {
        return underlying.root().keySet();
    }

    public String getString(String path) {
        return hasPath(path) ? underlying.getString(path) : null;
    }

    public String getString(String path, String defaultValue) {
        return hasPath(path) ? underlying.getString(path) : defaultValue;
    }

    public Boolean getBoolean(String path) {
        return hasPath(path) ? underlying.getBoolean(path) : null;
    }

    public Boolean getBoolean(String path, Boolean defaultValue) {
        return hasPath(path) ? underlying.getBoolean(path) : defaultValue;
    }

    public Integer getInt(String path) {
        return hasPath(path) ? underlying.getInt(path) : null;
    }

    public Integer getInt(String path, Integer defaultValue) {
        return hasPath(path) ? underlying.getInt(path) : defaultValue;
    }

    public Long getLong(String path) {
        return hasPath(path) ? underlying.getLong(path) : null;
    }

    public Long getLong(String path, Long defaultValue) {
        return hasPath(path) ? underlying.getLong(path) : defaultValue;
    }

    public Long getBytes(String path, Long defaultValue) {
        return hasPath(path) ? underlying.getBytes(path) : defaultValue;
    }

    public Long getMilliseconds(String path, Long defaultValue) {
        if (!hasPath(path)) {
            return defaultValue;
        }
        Duration duration = underlying.getDuration(path);
        return duration.toMillis();
    }

    public boolean hasPath(String path) {
        return underlying != null && underlying.hasPath(path);
    }
}
