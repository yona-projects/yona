package support;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 7. 16
 * Time: 오후 6:18
 * To change this template use File | Settings | File Templates.
 */
public class Config {
    public static HashMap<String, String> makeTestConfig() {
        HashMap<String, String> config = new HashMap<>(play.test.Helpers.inMemoryDatabase());
        config.put("ebean.default", "models.*");
        config.put("application.secret", "foo");
        return config;
    }
}
