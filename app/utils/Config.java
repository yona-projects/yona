package utils;

import org.apache.commons.lang.StringUtils;

public class Config {

    public static String getAuthority() {
        return getAuthority("localhost");
    }

    public static String getAuthority(String defaultValue) {
        String hostname = play.Configuration.root().getString("application.hostname");

        if (hostname != null && !hostname.isEmpty()) {
            Integer port = play.Configuration.root().getInt("application.port");

            if (port != null) {
               return hostname + ":" + port.toString();
            } else {
               return hostname;
            }
        } else {
           return defaultValue;
        }
    }

    public static String getProtocol() {
        return getProtocol("http");
    }

    public static String getProtocol(String defaultValue) {
        String protocol = play.Configuration.root().getString("application.protocol");

        if (protocol == null || protocol.isEmpty()) {
            return defaultValue;
        } else {
            return protocol;
        }
    }

    public static String createURL(String[] pathSegments, String defaultHost, String defaultProtocol) {
        String path = "/" + StringUtils.join(pathSegments, "/");
        return getProtocol(defaultProtocol) + "://" + getAuthority(defaultHost) + path;
    }
}
