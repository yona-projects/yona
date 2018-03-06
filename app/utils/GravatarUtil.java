/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package utils;

import controllers.UserApp;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class GravatarUtil {

    public static final String DEFAULT_GRAVATAR_URL = "https://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png";
    public static final int DEFAULT_SIZE = 64;

    public static String getAvatar(String email) {
        return getAvatar(email, DEFAULT_SIZE);
    }

    public static String getAvatar(String email, int size) {
        return getAvatar(email, size, DEFAULT_GRAVATAR_URL);
    }

    public static String getAvatar(String email, int size, String defaultImageUrl) {
        if(!Config.isConnectableToGravatarServer){
            return UserApp.DEFAULT_AVATAR_URL;
        }

        try {
            String url = "https://www.gravatar.com/avatar/" + MD5Util.md5Hex(email) + "?s=" + size;
            if(StringUtils.isNotEmpty(defaultImageUrl)) {
                url += "&d=" + URLEncoder.encode(defaultImageUrl, "UTF-8");
            }
            return url;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
