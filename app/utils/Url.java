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

import java.util.List;

import org.apache.commons.lang.StringUtils;
import play.Configuration;

public class Url {

    /**
     * Create a url with given <code>pathSegments</code> and configured scheme, hostname and port.
     *
     * @param pathSegments List of path segments to construct the path.
     *
     * @return <code>String</code> containing the created URL
     */
    public static String create(List<String> pathSegments) {
        return create(join(pathSegments));
    }

    public static String createWithContext(List<String> pathSegments) {
        String context = Configuration.root().getString("application.context");
        return create((context == null ? "" : context) + join(pathSegments));
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
    public static String create(List<String> pathSegments, String defaultHostport) {
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
    public static String create(List<String> pathSegments, String defaultHostport, String defaultScheme) {
        return create(join(pathSegments), defaultHostport, defaultScheme);
    }

    /**
     * Create a url with given <code>relativePath</code> and configured scheme, hostname and port.
     *
     * @param relativePath List of path segments to construct the path.
     *
     * @return <code>String</code> containing the created URL
     */
    public static String create(String relativePath) {
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
    public static String create(String relativePath, String defaultHostport) {
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
            String relativePath, String defaultHostport, String defaultScheme) {
        return Config.getScheme(defaultScheme) + "://" + Config.getHostport(defaultHostport) +
                relativePath;
    }

    private static String join(List<String> pathSegments) {
        return "/" + StringUtils.join(pathSegments, "/");
    }

    public static String removeFragment(String url) {
        int index = url.indexOf('#');
        return index >= 0 ? url.substring(0, index) : url;
    }
}
