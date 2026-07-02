package utils;

import play.mvc.Http;

import java.lang.reflect.Method;
import java.util.List;

public final class RequestUtil {
    private RequestUtil() {
    }

    public static String languageCode(Http.Request request) {
        if (request == null) {
            return "en";
        }
        try {
            Object languages = request.acceptLanguages();
            if (languages instanceof List && !((List<?>) languages).isEmpty()) {
                Object language = ((List<?>) languages).get(0);
                Method code = language.getClass().getMethod("code");
                Object value = code.invoke(language);
                if (value != null) {
                    return value.toString();
                }
            }
        } catch (Exception ignored) {
            // Fall back to English when the request has no usable language metadata.
        }
        return "en";
    }

    public static String getHeader(Http.Request request, String name) {
        if (request == null) {
            return null;
        }
        java.util.Optional<String> value = request.getHeaders().get(name);
        return value.orElse(null);
    }

    public static String getHeader(Http.RequestHeader request, String name) {
        if (request == null) {
            return null;
        }
        java.util.Optional<String> value = request.getHeaders().get(name);
        return value.orElse(null);
    }
}
