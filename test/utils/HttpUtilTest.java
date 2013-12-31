package utils;

import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.test.FakeApplication;
import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class HttpUtilTest {
    private FakeApplication app;

    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    @Test
    public void getFirstValueFromQuery() {
        {
            HashMap<String, String[]> query = new HashMap<>();
            String[] values = {"a", "b", "c"};
            query.put("test", values);
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo("a");
        }

        {
            HashMap<String, String[]> query = new HashMap<>();
            String[] values = {};
            query.put("test", values);
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo(null);
        }

        {
            HashMap<String, String[]> query = new HashMap<>();
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo(null);
        }
    }

}
