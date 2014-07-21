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
        additionalConfiguration.put("application.siteName", null);
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
        assertThat(Config.getSiteName()).isEqualTo("Yobi");
        Helpers.stop(app);
    }

    @Test
    public void semverize() {
        assertThat(Config.semverize("0.5.7")).isEqualTo("0.5.7");
        assertThat(Config.semverize("v0.5.7")).isEqualTo("0.5.7");
        assertThat(Config.semverize("0.5")).isEqualTo("0.5.0");
        assertThat(Config.semverize("0.4.0.pre")).isEqualTo("0.4.0-pre");
        assertThat(Config.semverize("0.5-alpha")).isEqualTo("0.5.0-alpha");
    }
}
