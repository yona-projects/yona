package play;

import org.slf4j.LoggerFactory;

public final class Logger {
    private static final org.slf4j.Logger DEFAULT = LoggerFactory.getLogger("application");

    private Logger() {
    }

    public static org.slf4j.Logger of(String name) {
        return LoggerFactory.getLogger(name);
    }

    public static void trace(String message) {
        DEFAULT.trace(message);
    }

    public static void trace(String message, Throwable error) {
        DEFAULT.trace(message, error);
    }

    public static void debug(String message) {
        DEFAULT.debug(message);
    }

    public static void debug(String message, Object... args) {
        DEFAULT.debug(message, args);
    }

    public static void debug(String message, Throwable error) {
        DEFAULT.debug(message, error);
    }

    public static void info(String message) {
        DEFAULT.info(message);
    }

    public static void info(String message, Object... args) {
        DEFAULT.info(message, args);
    }

    public static void warn(String message) {
        DEFAULT.warn(message);
    }

    public static void warn(String message, Object... args) {
        DEFAULT.warn(message, args);
    }

    public static void warn(String message, Throwable error) {
        DEFAULT.warn(message, error);
    }

    public static void error(String message) {
        DEFAULT.error(message);
    }

    public static void error(String message, Object... args) {
        DEFAULT.error(message, args);
    }

    public static void error(String message, Throwable error) {
        DEFAULT.error(message, error);
    }
}
