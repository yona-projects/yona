package utils;

import org.apache.pekko.actor.ActorSystem;
import play.Play;

public final class AkkaUtil {
    private AkkaUtil() {
    }

    public static ActorSystem system() {
        return Play.application().injector().instanceOf(ActorSystem.class);
    }
}
