package utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class HttpUtil {

    public static String getFirstValueFromQuery(Map<String, String[]> query, String name) {
        String[] values = query.get(name);

        if (values != null && values.length > 0) {
           return values[0];
        } else {
            return null;
        }
    }

}
