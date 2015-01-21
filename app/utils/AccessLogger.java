/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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

import controllers.UserApp;
import org.apache.commons.lang3.StringEscapeUtils;
import play.libs.F.Callback;
import play.libs.F.Promise;
import play.mvc.Http;
import play.mvc.Result;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AccessLogger {

    /**
     * Return "-" if the given string is null or return itself.
     *
     * @param value - the String to be checked
     * @return the String itself, {@code "-"} if null String input
     */
    static private String orHyphen(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        } else {
            return value;
        }
    }

    /**
     * Double-quote the given string or return "-" if it is null.
     *
     * @param value - the String to be double-quoted
     * @return the double-quoted string, {@code "-"} if null String input
     */
    static private String quotedOrHyphen(String value) {
        if (value == null) {
            return "-";
        } else {
            return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
        }
    }

    /**
     * Log a message for an HTTP request.
     *
     * This method is called by
     * {@link Global#onRequest(play.mvc.Http.Request, java.lang.reflect.Method)}
     * after an HTTP request is processed.
     *
     * The format follows Combined Log Format of Apache HTTP Server, except these:
     *
     * 1. The third field is username. The value comes from the Yobi's
     *    authentication scheme.
     * 2. The unit-postfixed time taken to serve the request is added at the
     *    end of log entry.
     * 3. The ident field and size of response in bytes is always empty with "-",
     *    because of functional limitation of Yobi.
     *
     * Here is an example:
     *
     * 127.0.0.1 - eungjun [16/Oct/2013:18:31:31 +0900] "GET /messages.js HTTP/1.1" 200 - "http://yobi:9000/" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.95 Safari/537.36" 18ms
     *
     * @param request an HTTP request
     * @param startTimeMillis the time in milliseconds when the request is received
     * @see <a href="http://httpd.apache.org/docs/2.2/en/logs.html#combined">Combined Log Format - Apache HTTP Server</a>
     * @see <a href="http://httpd.apache.org/docs/2.2/mod/mod_log_config.html#formats">Custom Log Formats - Apache HTTP Server</a>
     */
    public static void log(final Http.Request request, final Promise<Result> promise,
            final Long startTimeMillis) {
        final String username = UserApp.currentUser().loginId;
        promise.onRedeem(new Callback<Result>() {
            @Override
            public void invoke(final Result result) throws Throwable {
                log(request, username, result.toScala().header().status(),
                        startTimeMillis);
            }
        });
    }

    /**
     * Log a message for an HTTP request.
     *
     * This method is used by
     * {@link Global#onError(play.mvc.Http.RequestHeader, Throwable)},
     * {@link Global#onBadRequest(play.mvc.Http.RequestHeader, String)} and
     * {@link Global#onHandlerNotFound(play.mvc.Http.RequestHeader)}, which are
     * called if an error occurs while processing an HTTP request, to log a
     * message.
     *
     * This method is similar with
     * {@link #log(play.mvc.Http.Request, play.mvc.Result, Long)}, except this
     * requires an username and a status code instead of an HTTP response.
     *
     * @param request HTTP request header
     * @param username an username of HTTP request from {@link play.mvc.Http.Request#username()}
     * @param status HTTP status code
     * @see <a href="http://httpd.apache.org/docs/2.2/logs.html">Log Files - Apache HTTP Server</a>
     */
    public static void log(Http.RequestHeader request, String username, int status) {
        log(request, username, status, null);
    }

    /**
     * Log a message for an HTTP request.
     *
     * This method is used by
     * {@link #log(play.mvc.Http.Request, play.mvc.Result, Long)} and
     * {@link #log(play.mvc.Http.RequestHeader, String, in} to log a message.
     *
     * @param request HTTP request header
     * @param username an username of HTTP request from {@link play.mvc.Http.Request#username()}
     * @param status HTTP status code
     * @param startTimeMillis the time in milliseconds when the request is received
     * @see <a href="http://httpd.apache.org/docs/2.2/logs.html">Log Files - Apache HTTP Server</a>
     */
    private static void log(Http.RequestHeader request, String username, int status,
                            Long startTimeMillis) {
        if (request == null) {
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

        String time = (startTimeMillis != null) ?
                ((System.currentTimeMillis() - startTimeMillis) + "ms") : "-";

        String uri = request.uri();
        String entry = String.format("%s - %s [%s] \"%s %s %s\" %d - %s %s %s",
                request.remoteAddress(), orHyphen(username),
                format.format(new Date()), request.method(), request.uri(),
                request.version(), status,
                quotedOrHyphen(request.getHeader("Referer")),
                quotedOrHyphen(request.getHeader("User-Agent")),
                time);

        play.Logger.of("access." + uri).info(entry);
    }
}
