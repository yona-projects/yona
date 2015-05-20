/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package utils;

import com.typesafe.config.ConfigFactory;
import models.SiteAdmin;
import org.apache.commons.lang3.ObjectUtils;
import play.Configuration;
import play.mvc.Http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

public class Config {
    public static final String DEFAULT_SCHEME = "http";

    public static void onStart() {
        Diagnostic.register(new SimpleDiagnostic() {
            @Override
            public String checkOne() {
                Configuration config = Configuration.root();

                if (config.getInt("application.port") != null
                        && config.getInt("application.hostname") == null) {
                    return "application.port may be ignored because " +
                            "application.hostname is not configured.";
                } else {
                    return null;
                }
            }
        });
    }

    public static String getSiteName() {
        return ObjectUtils.defaultIfNull(
                play.Configuration.root().getString("application.siteName"), "Yobi");
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

        return "yobi@yobi.io";
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
        return yobi.BuildInfo.version();
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

    public static String getYobiHome() {
        return System.getProperty("yobi.home");
    }

    public static String getYobiHome(String defaultValue) {
        return System.getProperty("yobi.home", defaultValue);
    }
}
