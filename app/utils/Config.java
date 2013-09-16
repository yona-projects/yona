
package utils;

import play.Configuration;
import play.mvc.Http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

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
           if (play.Configuration.root().getInt("application.port") == null) {
               play.Logger.warn("application.port is ignored because application.hostname is not" +
                       "configured.");
           }
           return defaultValue;
        }
    }

    public static String getHostport() {
        Http.Context context = Http.Context.current.get();

        if (context != null) {
            return getHostport(context.request().host());
        } else {
            try {
                return java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                play.Logger.warn("Failed to get the host address", e);
                return "localhost";
            }
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

    public static String getScheme() {
        Http.Context context = Http.Context.current.get();

        if (context != null) {
            try {
                return Config.getScheme(new URI(context.request().uri()).getScheme());
            } catch (URISyntaxException e) {
                play.Logger.warn("Failed to get the scheme part from the request-uri", e);
                return getScheme("http");
            }
        } else {
            return getScheme("http");
        }
    }

    public static String getEmailFromSmtp() {
        Configuration config = Configuration.root();
        String user = config.getString("smtp.user");

        if (user == null) {
            return null;
        }

        if (user.contains("@")) {
            return user;
        } else {
            return user + "@" + config.getString("smtp.domain");
        }
    }

    /**
     * Make a full URI from the given {@code uri} and the configuration.
     *
     * The new url is created by copying the given url and fill scheme and
     * authority if they are empty.
     *
     * @param uri
     * @return
     * @throws URISyntaxException
     */
    public static URI createFullURI(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme();
        String authority = uri.getAuthority();

        scheme = (scheme != null) ? scheme : getScheme();
        authority = (authority != null) ? authority : getHostport();

        return new URI(
                scheme, authority, uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    /**
     * Make URI from the given {@code uri} and the configuration.
     *
     * @return uri
     * @throws URISyntaxException
     * @see {@link #createFullURI(java.net.URI)}
     */
    public static URI createFullURI(String uri) throws URISyntaxException {
        return createFullURI(new URI(uri));
    }
}
