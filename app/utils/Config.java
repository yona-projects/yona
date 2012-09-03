
package utils;
import org.apache.commons.lang.StringUtils;

public class Config {


    public static String getHostport(String defaultValue) {
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

    public static String getScheme(String defaultValue) {
        String scheme = play.Configuration.root().getString("application.scheme");

        if (scheme == null || scheme.isEmpty()) {
            return defaultValue;
        } else {
            return scheme;
        }
    }

    public static String createURL(String[] pathSegments, String defaultHost, String defaultScheme) {
        String path = "/" + StringUtils.join(pathSegments, "/");
        return getScheme(defaultScheme) + "://" + getHostport(defaultHost) + path;
    }

}
