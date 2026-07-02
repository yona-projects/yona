package utils;

import play.mvc.Controller;
import play.mvc.Http;

public class LegacyController extends Controller {
    public static Http.Request request() {
        return LegacyRequestContext.request();
    }

    public static LegacyResponse response() {
        return LegacyRequestContext.response();
    }

    public static LegacyRequestContext.LegacySession session() {
        return LegacyRequestContext.session();
    }

    public static String session(String key) {
        return LegacyRequestContext.session().get(key);
    }

    public static void session(String key, String value) {
        LegacyRequestContext.session().put(key, value);
    }

    public static LegacyRequestContext.LegacyFlash flash() {
        return LegacyRequestContext.flash();
    }

    public static void flash(String key, String value) {
        LegacyRequestContext.flash().put(key, value);
    }
}
