/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun, kjkmadness
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

import java.util.Comparator;
import java.util.Date;

public interface TimelineItem {
    /**
     * ascending comparator for {@code TimelineItem}
     */
    public static final Comparator<TimelineItem> ASC = new Comparator<TimelineItem>() {
        @Override
        public int compare(TimelineItem o1, TimelineItem o2) {
            return o1.getDate().compareTo(o2.getDate());
        }
    };

    /**
     * descending comparator for {@code TimelineItem}
     */
    public static final Comparator<TimelineItem> DESC = new Comparator<TimelineItem>() {
        @Override
        public int compare(TimelineItem o1, TimelineItem o2) {
            return o2.getDate().compareTo(o1.getDate());
        }
    };

    public Date getDate();
}
