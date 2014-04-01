
package utils;

import org.apache.commons.lang3.ObjectUtils;
import play.Configuration;
import play.mvc.Http;

import java.net.*;
import java.util.Enumeration;

public class Config {
    public static final String DEFAULT_SCHEME = "http";

    public static String getSiteName() {
        return ObjectUtils.defaultIfNull(
                play.Configuration.root().getString("application.siteName"), "Yobi");
    }

    public static String getHostport(String defaultValue) {
        play.Configuration config = play.Configuration.root();

        if (config == null) {
            return defaultValue;
        }

        String hostname = play.Configuration.root().getString("application.hostname");

        if (hostname != null && !hostname.isEmpty()) {
            Integer port = play.Configuration.root().getInt("application.port");

            if (port != null && !port.equals(80)) {
               return hostname + ":" + port.toString();
            } else {
               return hostname;
            }
        } else {
           if (play.Configuration.root().getInt("application.port") != null) {
               play.Logger.warn("application.port is ignored because application.hostname is not" +
                       " configured.");
           }
           return defaultValue;
        }
    }

    /**
     * @return the default IP address
     * @throws SocketException
     * @throws UnknownHostException
     */
    private static InetAddress getDefaultAddress() throws SocketException, UnknownHostException {
        Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
        while(n.hasMoreElements())  {
            Enumeration<InetAddress> a = n.nextElement().getInetAddresses();
            while(a.hasMoreElements()) {
                InetAddress address = a.nextElement();
                if (!address.isAnyLocalAddress() && (address instanceof Inet4Address)) {
                    return address;
                }
            }
        }
        return InetAddress.getLocalHost();
    }

    /**
     * Detect the hostname.
     *
     * Return {@code application.hostname} from the Yobi's configuration if
     * available or the hostname from the IP address from
     * {@link #getDefaultAddress()}.
     *
     * @return the hostname
     */
    public static String getHostname() {
        play.Configuration config = play.Configuration.root();

        if (config != null) {
            String hostname = play.Configuration.root().getString("application.hostname");
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
        }

        try {
            return getDefaultAddress().getHostName();
        } catch (Exception e) {
            play.Logger.warn("Failed to get the hostname", e);
            return "localhost";
        }
    }

    public static String getHostport() {
        Http.Context context = Http.Context.current.get();

        if (context != null) {
            return getHostport(context.request().host());
        } else {
            try {
                return getDefaultAddress().getHostAddress();
            } catch (Exception e) {
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
                return getScheme(getSchemeFromRequestURI(context.request()));
            } catch (URISyntaxException e) {
                play.Logger.warn("Failed to get the scheme part from the request-uri", e);
                return getScheme(DEFAULT_SCHEME);
            }
        } else {
            return getScheme(DEFAULT_SCHEME);
        }
    }

    private static String getSchemeFromRequestURI(Http.Request request) throws URISyntaxException {
        String scheme = new URI(request.uri()).getScheme();
        if (scheme == null) {
            return DEFAULT_SCHEME;
        } else {
            return scheme;
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
