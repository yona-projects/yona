package utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class GravatarUtil {

    public static final String DEFAULT_GRAVATAR_URL = "http://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png";

    public static String getAvatar(String email) {
        String avatarUrl = "http://www.gravatar.com/avatar/" + MD5Util.md5Hex(email);
        return avatarUrl;
    }

    public static String getAvatar(String email, int size) {
        return getAvatar(email, size, DEFAULT_GRAVATAR_URL);
    }

    public static String getAvatar(String email, int size, String defaultImageUrl) {
        try {
            return getAvatar(email) + "?s=" + size + "&d=" + URLEncoder.encode(defaultImageUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
