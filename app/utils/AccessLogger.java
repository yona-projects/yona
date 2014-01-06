package utils;

import controllers.UserApp;
import models.User;
import play.api.mvc.PlainResult;
import play.mvc.Http;
import play.mvc.Result;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AccessLogger {

    /**
     * {@code value}가 null이면 "-"을, 아니면 그대로 반환한다.
     *
     * @param value
     * @return {@code value}가 null이면 {@code "-"}, 그렇지 않다면 {@code value}와 같은 문자열
     */
    static private String orHyphen(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        } else {
            return value;
        }
    }

    /**
     * {@code value}가 null이면 "-"을, 아니면 그대로 반환한다.
     *
     * @param value
     * @return {@code value}가 null이면 {@code "-"}, 그렇지 않다면 {@code value}를 큰따옴표로 감싼 문자열
     */
    static private String quotedOrHyphen(String value) {
        if (value == null) {
            return "-";
        } else {
            return "\"" + value + "\"";
        }
    }

    /**
     * Access Log를 남긴다.
     *
     * when: 매 HTTP 요청이 있을 때 마다 수행되는 {@link Global#onRequest(play.mvc.Http.Request,
     * java.lang.reflect.Method)}에서, 요청의 처리가 완료되면 이 메소드가 호출되도록 한다.
     *
     * 로그 포맷은 다음의 예와 같이 Apache HTTP Server의 Combined Log Format을 따르나 다음의 두 가지가
     * 다르다.
     *
     * 1. 세 번째 필드에는 현재 사용자의 아이디가 온다. 이 값은 HTTP 인증(Basic Authentication 등)을 통해
     * 얻은 것이 아닐 수도 있다.
     * 2. log entry의 끝에 요청이 처리되는데 걸린 시간이 추가된다.
     *
     * 127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif" 200 - "http://www
     * .example.com/start.html" "Mozilla/4.08 [en] (Win98; I ;Nav)" 70ms
     *
     * 또한 현재 기능상의 한계로 ident와 요청의 길이는 항상 값이 비어있는 상태로 로그가 남게 된다.
     *
     * TODO: 요청의 길이는 기록할 수 있다면 유용할 것이다.
     *
     * @param request HTTP 요청
     * @param startTimeMillis 요청을 처음 받은 시간 (밀리초 단위)
     * @see <a href="http://httpd.apache.org/docs/2.2/logs.html">Log Files - Apache HTTP Server</a>
     */
    public static Result log(Http.Request request, Result result, Long startTimeMillis) {
        if (!(result.getWrappedResult() instanceof play.api.mvc.PlainResult)) {
            return result;
        }

        int status = ((PlainResult) result.getWrappedResult()).header().status();

        log(request, UserApp.currentUser().loginId, status, startTimeMillis);

        return result;
    }

    /**
     * Access Log를 남긴다.
     *
     * when: HTTP 요청 처리 중에 오류가 발생했을 때 호출되는 {@link Global#onError(play.mvc.Http.RequestHeader,
     * Throwable)}, {@link Global#onBadRequest(play.mvc.Http.RequestHeader, String)},
     * {@link Global#onHandlerNotFound(play.mvc.Http.RequestHeader)} 에서 로그를 남기기 위해 이 메소드를 호출한다.
     *
     * {@link #log(play.mvc.Http.Request, play.mvc.Result, Long)}와 같지만,
     * 응답 대신 사용자 이름과 상태코드를 받는다는 점이 다르다.
     *
     * @param request HTTP 요청 헤더
     * @param username {@link play.mvc.Http.Request#username()}를 통해 얻을 수 있는, HTTP 요청의 사용자 이름
     * @param status HTTP 응답의 상태 코드
     * @see <a href="http://httpd.apache.org/docs/2.2/logs.html">Log Files - Apache HTTP Server</a>
     */
    public static void log(Http.RequestHeader request, String username, int status) {
        log(request, username, status, null);
    }

    /**
     * Access Log를 남긴다.
     *
     * when: {@link #log(play.mvc.Http.Request, play.mvc.Result, Long)}와
     * {@link #log(play.mvc.Http.RequestHeader, String, int)}가 이 메소드를 호출해서 로그를 남긴다.
     *
     * @param request HTTP 요청 헤더
     * @param username {@link play.mvc.Http.Request#username()}를 통해 얻을 수 있는, HTTP 요청의 사용자 이름
     * @param status HTTP 응답의 상태 코드
     * @param startTimeMillis 요청을 처음 받은 시간 (밀리초 단위)
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
