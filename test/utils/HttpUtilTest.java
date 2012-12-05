package utils;

import java.util.HashMap;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class HttpUtilTest {

    @Test
    public void getFirstValueFromQuery() {
        {
            HashMap<String, String[]> query = new HashMap<String, String[]>();
            String[] values = {"a", "b", "c"};
            query.put("test", values);
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo("a");
        }

        {
            HashMap<String, String[]> query = new HashMap<String, String[]>();
            String[] values = {};
            query.put("test", values);
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo(null);
        }

        {
            HashMap<String, String[]> query = new HashMap<String, String[]>();
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo(null);
        }
    }

}