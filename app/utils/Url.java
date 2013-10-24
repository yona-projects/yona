package utils;

import java.net.*;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class Url {

    /**
     * Create a url with given <code>pathSegments</code> and configured scheme, hostname and port.
     *
     * @param pathSegments List of path segments to construct the path.
     *
     * @return <code>String</code> containing the created URL
     */
    public static String create(List<String> pathSegments) throws MalformedURLException {
        return create(join(pathSegments));
    }
    
    /**
     * Create a url with given <code>pathSegments</code> and configured scheme, hostname and port.
     * 
     * If hostname is not configured, use defaultHostport as hostname and port.
     * 
     * @param pathSegments List of path segments to construct the path.
     * @param defaultHostport
     * 
     * @return <code>String</code> containing the created URL
     * 
     */
    public static String create(List<String> pathSegments, String defaultHostport) throws MalformedURLException {
        return create(join(pathSegments), defaultHostport);
    }
    
    /**
     * Create a url with given <code>pathSegments</code> and configured scheme, hostname and port.
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
    public static String create(List<String> pathSegments, String defaultHostport, String defaultScheme) throws MalformedURLException {
        return create(join(pathSegments), defaultHostport, defaultScheme);
    }

    /**
     * Create a url with given <code>relativePath</code> and configured scheme, hostname and port.
     *
     * @param relativePath List of path segments to construct the path.
     *
     * @return <code>String</code> containing the created URL
     */
    public static String create(String relativePath) throws MalformedURLException {
        return create(relativePath, Config.getHostport());
    }

    /**
     * Create a url with given <code>relativePath</code> and configured scheme, hostname and port.
     *
     * If hostname is not configured, use defaultHostport as hostname and port.
     *
     * @param relativePath
     * @param defaultHostport
     *
     * @return <code>String</code> containing the created URL
     *
     */
    public static String create(String relativePath, String defaultHostport) throws MalformedURLException {
        return create(relativePath, defaultHostport, Config.getScheme());
    }

    /**
     * Create a url with given <code>relativePath</code> and configured scheme, hostname and port.
     *
     * If scheme is not configured, use defaultScheme as the scheme.
     * If hostname is not configured, use defaultHostport as the hostname and the port.
     *
     * @param relativePath
     * @param defaultHostport
     * @param defaultScheme
     *
     * @return <code>String</code> containing the created URL
     *
     */
    public static String create(
            String relativePath, String defaultHostport, String defaultScheme)
            throws MalformedURLException {
        return new URL(
                Config.getScheme(defaultScheme), Config.getHostport(defaultHostport), relativePath)
                .toString();
    }

    private static String join(List<String> pathSegments) {
        return "/" + StringUtils.join(pathSegments, "/");
    }
}