/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import org.joda.time.DateTime;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class MomentUtilTest {

    @Test
    public void moment() {
        // Given
        DateTime now = new DateTime();
        DateTime oneDayBefore = now.minusDays(1);

        // When
        JSInvocable invocable = MomentUtil.newMoment(oneDayBefore.getMillis());
        String result = invocable.invoke("fromNow");

        // Then
        assertThat(result).isEqualTo("a day ago");
    }

    @Test
    public void momentWithLanguage() {
        // Given
        DateTime now = new DateTime();
        DateTime oneDayBefore = now.minusDays(1);

        // When
        JSInvocable invocable = MomentUtil.newMoment(oneDayBefore.getMillis(), "ko");
        String result = invocable.invoke("fromNow");

        // Then
        assertThat(result).isEqualTo("하루 전");
    }
}
