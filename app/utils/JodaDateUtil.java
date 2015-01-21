/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Sangcheol Hwang
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

import org.apache.commons.lang3.StringUtils;
import org.joda.time.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class JodaDateUtil {
    public static String getDateString(Date date) {
        return getDateString(date, null);
    }
    public static String getDateString(Date date, String format) {
        if(StringUtils.isEmpty(format)) {
            format = "yyyy-MM-dd HH:mm:ss";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }
    public static Date today() {
        return LocalDate.now().toDate();
    }

    public static Date now() {
        return DateTime.now().toDate();
    }

    public static Duration ago(DateTime time) {
        return new Duration(time, DateTime.now());
    }

    public static Duration ago(Date time) {
        return new Duration(new DateTime(time), DateTime.now());
    }

    public static Duration ago(Long time){
        return new Duration(new DateTime(new Date(time)), DateTime.now());
    }

    public static Date before(int days){
        return new DateTime(today()).minusDays(days).toDate();
    }

    public static Date beforeByMillis(long millis){
        return new DateTime(today()).minus(millis).toDate();
    }

    public static String momentFromNow(Long time) {
        return momentFromNow(time, Constants.DEFAULT_LANGUAGE);
    }

    public static String momentFromNow(Long time, String language) {
        JSInvocable moment = MomentUtil.newMoment(time, language);
        return moment.invoke("fromNow");
    }

    public static String momentFromNow(Date time) {
        return momentFromNow(time, Constants.DEFAULT_LANGUAGE);
    }

    public static String momentFromNow(Date time, String language) {
        JSInvocable moment = MomentUtil.newMoment(time.getTime(), language);
        return moment.invoke("fromNow");
    }

    public static int localDaysBetween(Date from, Date to) {
        return Days.daysBetween(new DateTime(from).toLocalDate(), new DateTime(to).toLocalDate()).getDays();
    }

    /**
     * Force update HH:mm:ss -> 23:59:59
     */
    public static Date lastSecondOfDay(Date date) {
        if (date == null) {
            return null;
        }
        DateTime dateTime = new DateTime(date);
        return dateTime.withField(DateTimeFieldType.hourOfDay(), 23)
                .withField(DateTimeFieldType.minuteOfHour(), 59)
                .withField(DateTimeFieldType.secondOfMinute(), 59).toDate();
    }
}
