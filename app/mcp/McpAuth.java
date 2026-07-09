/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package mcp;

import controllers.UserApp;
import models.User;
import org.apache.commons.lang3.StringUtils;
import play.Configuration;
import play.mvc.Http;
import utils.LegacyRequestContext;
import utils.RequestUtil;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

public final class McpAuth {
    private McpAuth() {
    }

    public static boolean isEnabled() {
        return Configuration.root().getBoolean("mcp.enabled", false);
    }

    public static boolean isAllowedOrigin(Http.Request request) {
        String origin = RequestUtil.getHeader(request, "Origin");
        if (StringUtils.isBlank(origin)) {
            return true;
        }

        String allowedOrigins = Configuration.root().getString("mcp.allowedOrigins", "");
        if (StringUtils.isNotBlank(allowedOrigins)) {
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .anyMatch(origin::equalsIgnoreCase);
        }

        return isSameHost(origin, request.host()) || isLocalhost(origin);
    }

    public static User authenticate(Http.Request request) {
        String token = tokenFromAuthorization(RequestUtil.getHeader(request, "Authorization"));
        if (StringUtils.isBlank(token)) {
            token = RequestUtil.getHeader(request, UserApp.USER_TOKEN_HEADER);
        }
        return User.findByUserToken(token);
    }

    public static void bind(User user) {
        LegacyRequestContext.args().put(UserApp.TOKEN_USER, user);
    }

    private static String tokenFromAuthorization(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            return null;
        }

        String trimmed = authorization.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bearer ")) {
            return trimmed.substring("bearer ".length()).trim();
        }
        if (lower.startsWith("token ")) {
            return trimmed.substring("token ".length()).trim();
        }
        return null;
    }

    private static boolean isSameHost(String origin, String requestHost) {
        try {
            URI uri = URI.create(origin);
            return requestHost.equalsIgnoreCase(uri.getAuthority());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isLocalhost(String origin) {
        try {
            String host = URI.create(origin).getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
