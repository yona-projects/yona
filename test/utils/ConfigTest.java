package utils;

import java.util.Map;

import org.junit.Test;

import play.test.FakeApplication;
import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class ConfigTest {
    @Test
    public void getScheme() {
        FakeApplication app;
        Map<String, String> additionalConfiguration = support.Helpers.makeTestConfig();

        additionalConfiguration.put("application.scheme", "http");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getScheme("https")).isEqualTo("http");
        Helpers.stop(app);

        additionalConfiguration.put("application.scheme", "");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getScheme("https")).isEqualTo("https");
        Helpers.stop(app);
    }

    @Test
    public void getHostname() {
        FakeApplication app;
        Map<String, String> additionalConfiguration = support.Helpers.makeTestConfig();

        additionalConfiguration.put("application.hostname", "test.yobi.com");
        additionalConfiguration.put("application.port", "8080");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost")).isEqualTo("test.yobi.com:8080");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", "test.yobi.com");
        additionalConfiguration.put("application.port", null);
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("test.yobi.com");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", null);
        additionalConfiguration.put("application.port", "8080");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("localhost:9000");
        Helpers.stop(app);

        additionalConfiguration.put("application.hostname", null);
        additionalConfiguration.put("application.port", null);
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getHostport("localhost:9000")).isEqualTo("localhost:9000");
        Helpers.stop(app);
    }

    @Test
    public void getSiteName() {
        FakeApplication app;
        Map<String, String> additionalConfiguration = support.Helpers.makeTestConfig();

        additionalConfiguration.put("application.siteName", "my site");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getSiteName()).isEqualTo("my site");
        Helpers.stop(app);

        // The default is "Yobi".
        additionalConfiguration.put("application.scheme", null);
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getScheme("https")).isEqualTo("Yobi");
        Helpers.stop(app);
    }
}
