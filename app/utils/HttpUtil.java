package utils;

import org.apache.commons.lang3.StringUtils;
import play.api.http.MediaRange;
import play.mvc.Http;
import scala.Option;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

public class HttpUtil {

    public static String getFirstValueFromQuery(Map<String, String[]> query, String name) {
        String[] values = query.get(name);

        if (values != null && values.length > 0) {
           return values[0];
        } else {
            return null;
        }
    }

    public static String encodeContentDisposition(String filename)
            throws UnsupportedEncodingException {
        // Encode the filename with RFC 2231; IE 8 or less, and Safari 5 or less
        // are not supported. See http://greenbytes.de/tech/tc2231/
        filename = filename.replaceAll("[:\\x5c\\/{?]", "_");
        filename = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
        filename = "filename*=UTF-8''" + filename;
        return filename;
    }

    public static String getPreferType(Http.Request request, String ... types) {
        // acceptedTypes is sorted by preference.
        for(MediaRange range : request.acceptedTypes()) {
            for(String type : types) {
                if (range.accepts(type)) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * 주어진 {@code url}의 query string에 주어진 key-value pair들을 추가하여 만든 url을 반환한다.
     *
     * key-value pair의 형식은 {@code key=value}이다.
     *
     * @param url
     * @param encodedPairs
     * @return
     * @throws URISyntaxException
     */
    public static String addQueryString(String url, String ... encodedPairs) throws
            URISyntaxException {
        URI aURI = new URI(url);
        String query = (aURI.getQuery() != null) ? aURI.getQuery() : "";
        query += (query.length() > 0 ? "&" : "") + StringUtils.join(encodedPairs, "&");

        return new URI(aURI.getScheme(), aURI.getAuthority(), aURI.getPath(), query,
                aURI.getFragment()).toString();
    }

    /**
     * 주어진 {@code url}의 query string에서 주어진 {@code keys}에 해당하는 모든 key-value pair를 제외시켜
     * 만든 url을 반환한다.
     *
     * key-value pair의 형식은 {@code key=value}이다.
     *
     * @param url
     * @param keys query string에서 제거할 key. 인코딩되어있어서는 안된다.
     * @return
     * @throws URISyntaxException
     * @throws UnsupportedEncodingException
     */
    public static String removeQueryString(String url, String ... keys) throws
            URISyntaxException, UnsupportedEncodingException {
        URI aURI = new URI(url);

        if (aURI.getQuery() == null) {
            return url;
        }

        List<String> pairStrings = new ArrayList<>();
        Set<String> keySet = new HashSet<>(Arrays.asList(keys));

        for (String pairString : aURI.getQuery().split("&")) {
            String[] pair = pairString.split("=");
            if (pair.length == 0) {
                continue;
            }
            if (!keySet.contains(URLDecoder.decode(pair[0], "UTF-8"))) {
                pairStrings.add(pairString);
            }
        }

        return new URI(aURI.getScheme(), aURI.getAuthority(), aURI.getPath(),
                StringUtils.join(pairStrings, "&"), aURI.getFragment()).toString();
    }

    /**
     * 주어진 Http.Request 에서 X-Requested-With 헤더가 존재하며
     * 그 값이 XMLHttpRequest 인지의 여부를 Boolean 으로 반환한다.
     *
     * jQuery, prototype, JindoJS 등 대부분의 JavaScript framework 에서
     * XHR 호출시 이 헤더에 이 값을 설정하여 전송한다.
     *
     * @param request
     * @return Boolean
     */
    public static Boolean isRequestedWithXHR(Http.Request request){
        String requestedWith = request.getHeader("X-Requested-With");
        return (requestedWith != null && requestedWith.toLowerCase().equals("xmlhttprequest"));
    }
}
