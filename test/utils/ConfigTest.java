package utils;

import java.util.HashMap;

import org.junit.Test;

import play.test.FakeApplication;
import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class ConfigTest {
    @Test
    public void getScheme() {
        FakeApplication app;
        HashMap<String, String> additionalConfiguration = support.Config.makeTestConfig();

        additionalConfiguration.put("application.scheme", "http");
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getScheme("https")).isEqualTo("http");
        Helpers.stop(app);

        additionalConfiguration.put("application.scheme", "");
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getScheme("https")).isEqualTo("https");
        Helpers.stop(app);
    }

    @Test
    public void getHostname() {
        FakeApplication app;
        HashMap<String, String> additionalConfiguration = support.Config.makeTestConfig();

        additionalConfiguration.put("application.hostname", "www.nforge.com");
        additionalConfiguration.put("application.port", "8080");
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost")).isEqualTo("www.nforge.com:8080");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", "www.nforge.com");
        additionalConfiguration.put("application.port", null);
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("www.nforge.com");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", null);
        additionalConfiguration.put("application.port", "8080");
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("localhost:9000");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", null);
        additionalConfiguration.put("application.port", null);
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("localhost:9000");
        Helpers.stop(app);
    }

}
