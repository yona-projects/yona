package support;

import java.util.HashMap;

/**
 * User: nori
 * Date: 13. 7. 16
 * Time: 오후 6:18
 */
public class Config {
    public static HashMap<String, String> makeTestConfig() {
        HashMap<String, String> config = new HashMap<>(play.test.Helpers.inMemoryDatabase());
        config.put("ebean.default", "models.*");
        config.put("application.secret", "foo");
        return config;
    }
}
