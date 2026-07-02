package utils;

import play.Play;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;

public final class WSClientUtil {
    private WSClientUtil() {
    }

    public static WSRequest url(String url) {
        return Play.application().injector().instanceOf(WSClient.class).url(url);
    }
}
