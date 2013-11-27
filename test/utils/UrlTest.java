package utils;

import java.util.Arrays;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.test.FakeApplication;
import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class UrlTest {
    protected static FakeApplication app;
    private Map<String, String> additionalConfiguration;

    @Before
    public void before() {
        additionalConfiguration = support.Config.makeTestConfig();
        additionalConfiguration.put("application.scheme", "http");
        additionalConfiguration.put("application.hostname", "localhost");
        additionalConfiguration.put("application.port", "9999");
        app = Helpers.fakeApplication(additionalConfiguration);
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test
    public void create() {
        String actual = Url.create(Arrays.asList("path", "to", "somewhere"));
        String expected = "http://localhost:9999/path/to/somewhere";

        assertThat(actual).isEqualTo(expected);
    }
}
