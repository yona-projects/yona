
package utils;

public class Config {

    public static String getHostport(String defaultValue) {
        play.Configuration config = play.Configuration.root();

        if (config == null) {
            return defaultValue;
        }

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
        play.Configuration config = play.Configuration.root();

        if (config == null) {
            return defaultValue;
        }

        String scheme = config.getString("application.scheme");

        if (scheme == null || scheme.isEmpty()) {
            return defaultValue;
        } else {
            return scheme;
        }
    }

}
