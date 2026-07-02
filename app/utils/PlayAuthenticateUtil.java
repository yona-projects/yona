package utils;

import com.feth.play.module.pa.PlayAuthenticate;
import play.Play;

public final class PlayAuthenticateUtil {
    private PlayAuthenticateUtil() {
    }

    public static PlayAuthenticate get() {
        return Play.application().injector().instanceOf(PlayAuthenticate.class);
    }
}
