/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package utils;

import models.SiteAdmin;
import org.apache.commons.lang3.ObjectUtils;
import play.Configuration;
import play.mvc.Http;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Enumeration;

public class Config {
    public static final String DEFAULT_SCHEME = "http";
    private static final String YONA_DATA = "yona.data"; //property from java -Dyona.data option string
    public static boolean isConnectableToGravatarServer = true;

    public static void onStart() {
        Diagnostic.register(new SimpleDiagnostic() {
            @Override
            public String checkOne() {
                Configuration config = Configuration.root();

                if (config.getInt("application.port") != null
                        && config.getString("application.hostname") == null) {
                    return "application.port may be ignored because " +
                            "application.hostname is not configured.";
                } else {
                    return null;
                }
            }
        });

        isConnectableToGravatarServer = isConnectableToGravatar();
    }

    public static String getSiteName() {
        return ObjectUtils.defaultIfNull(
                play.Configuration.root().getString("application.siteName"), "Yona");
    }

    public static String getContextRoot(){
        return play.Configuration.root().getString("application.context", "/");
    }

    public static String getHostport(String defaultValue) {
        String hostname = play.Configuration.root().getString("application.hostname");

        if (hostname != null && !hostname.isEmpty()) {
            Integer port = play.Configuration.root().getInt("application.port");

            if (port != null && !port.equals(80)) {
               return hostname + ":" + port.toString();
            } else {
               return hostname;
            }
        } else {
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

        String hostname = play.Configuration.root().getString("application.hostname");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
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

    public static String getSystemEmailAddress() {
        String email = getEmailFromSmtp();

        if (email != null) {
            return email;
        }

        models.User admin = (new SiteAdmin()).admin;

        if (admin != null && admin.email != null) {
            return admin.email;
        }

        return "yona@yona.io";
    }

    public static String getEmailFromSmtp() {
        return getEmail("smtp");
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

    /**
     * Convert the given version to be compatible with
     * <a href="http://semver.org/">semver</a>.
     *
     * Examples:
     *   v0.5.7 -> 0.5.7
     *   0.5 -> 0.5.0
     *   0.4.0.pre -> 0.4.0-pre
     *
     * @param ver  a version string to be semverized
     * @return     the semverized string
     */
    public static String semverize(String ver) {
        // v0.5.7 -> 0.5.7
        ver = ver.replaceFirst("^v", "");

        // 0.4.0.pre -> 0.4.0-pre
        ver = ver.replaceAll("\\.([^\\d]+)","-$1");

        // 0.5 -> 0.5.0
        // 0.5-alpha -> 0.5.0-alpha
        ver = ver.replaceFirst("^(\\d+\\.\\d+)($|-)", "$1.0$2");

        return ver;
    }

    /**
     * Return the version of Yobi installed currently.
     *
     * @return  the current version
     */
    public static String getCurrentVersion() {
        return yona.BuildInfo.version();
    }

    public static String getEmailFromImap() {
        return Configuration.root().getString("imap.address", getEmail("imap"));
    }

    private static String getEmail(String prefix) {
        Configuration config = Configuration.root();
        String user = config.getString(prefix + ".user");

        if (user == null) {
            return null;
        }

        if (user.contains("@")) {
            return user;
        } else {
            return user + "@" + config.getString(prefix + ".domain", getHostname());
        }
    }

    public static Charset getCharset() {
        return Charset.forName("UTF-8");
    }

    public static String getYonaDataDir() {
        return System.getProperty(YONA_DATA);
    }

    public static String getYonaDataDir(String defaultValue) {
        return System.getProperty(YONA_DATA, defaultValue);
    }

    public static boolean displayPrivateRepositories() {
        return Configuration.root().getBoolean("application.displayPrivateRepositories", Boolean.FALSE);
    }

    private static boolean isConnectableToGravatar() {
        try {
            return InetAddress.getByName("ko.gravatar.com").isReachable(100)
                    && InetAddress.getByName("www.gravatar.com").isReachable(100);
        } catch (IOException e) {
            play.Logger.warn("Gravatar server is unreachable. Gravatar service will not work.");
            return false;
        }
    }
}
