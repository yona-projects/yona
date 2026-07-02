package utils;

import play.mvc.Http;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyResponse {
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final List<Http.Cookie> cookies = new ArrayList<>();
    private final List<String> discardedCookies = new ArrayList<>();

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void setContentType(String contentType) {
        setHeader(Http.HeaderNames.CONTENT_TYPE, contentType);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setCookie(Http.Cookie cookie) {
        cookies.add(cookie);
    }

    public void discardCookie(String name) {
        discardedCookies.add(name);
    }

    Result applyTo(Result result) {
        Result updated = result;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            updated = updated.withHeader(header.getKey(), header.getValue());
        }
        for (Http.Cookie cookie : cookies) {
            updated = updated.withCookies(cookie);
        }
        for (String cookie : discardedCookies) {
            updated = updated.discardingCookie(cookie);
        }
        return updated;
    }
}
