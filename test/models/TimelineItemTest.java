/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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

import static org.fest.assertions.Assertions.*;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.*;

public class TimelineItemTest {
    private TimelineItem first;
    private TimelineItem second;
    private TimelineItem third;
    private List<TimelineItem> list;

    @Before
    public void given() throws Exception {
        // Given
        first = createTimelineItem("2013-10-01");
        second = createTimelineItem("2013-10-02");
        third = createTimelineItem("2013-10-03");
        list = new ArrayList<>();
        list.add(second);
        list.add(third);
        list.add(first);
    }


    @Test
    public void asc() {
        // When
        Collections.sort(list, TimelineItem.ASC);

        // Then
        assertThat(list).containsExactly(first, second, third);
    }

    @Test
    public void desc() {
        // When
        Collections.sort(list, TimelineItem.DESC);

        // Then
        assertThat(list).containsExactly(third, second, first);
    }

    private TimelineItem createTimelineItem(String str) throws ParseException {
        final Date date = DateUtils.parseDate(str, "yyyy-MM-dd");
        return new TimelineItem() {
            @Override
            public Date getDate() {
                return date;
            }
        };
    }
}
