/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

package models;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import play.test.FakeApplication;
import play.test.Helpers;

import static org.fest.assertions.Assertions.assertThat;

public class NotificationMailTest {
    protected static FakeApplication app;
    private static Map<String, String> additionalConfiguration;

    @BeforeClass
    public static void before() {
        additionalConfiguration = support.Helpers.makeTestConfig();
        additionalConfiguration.put("application.noreferrer", "true");
        additionalConfiguration.put("application.hostname", "yobi.io");
        app = support.Helpers.makeTestApplication(additionalConfiguration);
        Helpers.start(app);
    }

    @AfterClass
    public static void after() {
        Helpers.stop(app);
    }

    @Test
    public void handleLinks() {
        // Given
        Document doc = Jsoup.parse(
            "<a href=\"http://y/foo/bar\">external link</a>" +
            "<a href=\"http://y/foo/bar\" rel=\"nofollow\">external link</a>" +
            "<a href=\"http://yobi.io/foo/bar\">internal link</a>" +
            "<a href=\"/foo/bar\">relative link</a>" +
            "<a href=\"http://yobi.io/%ag\">malformed link</a>");

        // When
        NotificationMail.handleLinks(doc);

        // Then
        Elements links = doc.select("a");

        assertThat(links.get(0).attr("rel"))
            .describedAs("rel attribute of external link")
            .isEqualTo(" noreferrer");

        assertThat(links.get(1).attr("rel"))
            .describedAs("rel attribute of external link whose rel attribute was 'nofollow'")
            .isEqualTo("nofollow noreferrer");

        assertThat(links.get(2).hasAttr("rel"))
            .describedAs("rel attribute of internal link contains 'noreferrer'.")
            .isFalse();

        assertThat(links.get(3).hasAttr("rel"))
            .describedAs("rel attribute of relative link contains 'noreferrer'.")
            .isFalse();

        assertThat(links.get(4).attr("rel"))
            .describedAs("rel attribute of malformed link")
            .isEqualTo(" noreferrer");

    }
}
