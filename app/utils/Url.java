package utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class Url {

    /**
     * Create url with given <code>pathSegments</code> and configured scheme, hostname and port.
     *
     * @param pathSegments List of path segments to construct the path.
     *
     * @return <code>String</code> containing the created URL
     */
    public static String create(List<String> pathSegments) {
        return create(pathSegments, Config.getHostport());
    }
    
    /**
     * Create url with given <code>pathSegments</code> and configured scheme, hostname and port.
     * 
     * If hostname is not configured, use defaultHostport as hostname and port.
     * 
     * @param pathSegments List of path segments to construct the path.
     * @param defaultHostport
     * 
     * @return <code>String</code> containing the created URL
     * 
     */
    public static String create(List<String> pathSegments, String defaultHostport) {
        return create(pathSegments, defaultHostport, Config.getScheme());
    }
    
    /**
     * Create url with given <code>pathSegments</code> and configured scheme, hostname and port.
     * 
     * If scheme is not configured, use defaultScheme as the scheme.
     * If hostname is not configured, use defaultHostport as the hostname and the port.
     * 
     * @param pathSegments List of path segments to construct the path.
     * @param defaultHostport
     * @param defaultScheme
     * 
     * @return <code>String</code> containing the created URL
     * 
     */
    public static String create(List<String> pathSegments, String defaultHostport, String defaultScheme) {
        String path = "/" + StringUtils.join(pathSegments, "/");
        return Config.getScheme(defaultScheme) + "://" + Config.getHostport(defaultHostport) + path;
    }
    
}