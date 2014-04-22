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
        additionalConfiguration = support.Helpers.makeTestConfig();
        additionalConfiguration.put("application.scheme", "http");
        additionalConfiguration.put("application.hostname", "localhost");
        additionalConfiguration.put("application.port", "9999");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
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
