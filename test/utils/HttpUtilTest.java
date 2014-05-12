/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            assertThat(actual).isEqualTo("");
        }

        {
            HashMap<String, String[]> query = new HashMap<>();
            String actual = HttpUtil.getFirstValueFromQuery(query, "test");
            assertThat(actual).isEqualTo("");
        }
    }

}
