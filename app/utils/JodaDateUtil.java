/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp.
 *  https://yona.io
 **/
package utils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JodaDateUtil {
    public static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    public static String getDateString(Date date) {
        return getDateString(date, null);
    }
    public static String getDateString(Date date, String format) {
        if(StringUtils.isEmpty(format)) {
            format = "yyyy-MM-dd h:mm:ss a";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        if (date == null) {
            return "";
        }
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
        if (time == null) {
            return "";
        }
        return fromNow(new DateTime(time), language);
    }

    public static String momentFromNow(Date time) {
        return momentFromNow(time, Constants.DEFAULT_LANGUAGE);
    }

    public static String momentFromNow(Date time, String language) {
        if (time == null) {
            return "";
        }
        return fromNow(new DateTime(time), language);
    }

    private static String fromNow(DateTime targetTime, String language) {
        long diffMillis = targetTime.getMillis() - DateTime.now().getMillis();
        boolean future = diffMillis > 0;
        long seconds = Math.abs(diffMillis) / 1000;

        if (seconds < 45) {
            return isKorean(language) ? "방금 전" : (future ? "in a few seconds" : "a few seconds ago");
        }

        TimeAmount amount = relativeAmount(seconds);
        if (isKorean(language)) {
            return amount.value + amount.koreanUnit + (future ? " 후" : " 전");
        }

        String unit = amount.englishUnit;
        if (amount.value != 1) {
            unit += "s";
        }
        return future ? "in " + amount.value + " " + unit : amount.value + " " + unit + " ago";
    }

    private static TimeAmount relativeAmount(long seconds) {
        if (seconds < 90) {
            return new TimeAmount(1, "분", "minute");
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 45) {
            return new TimeAmount(minutes, "분", "minute");
        }
        if (minutes < 90) {
            return new TimeAmount(1, "시간", "hour");
        }
        long hours = Math.round(minutes / 60.0);
        if (hours < 22) {
            return new TimeAmount(hours, "시간", "hour");
        }
        if (hours < 36) {
            return new TimeAmount(1, "일", "day");
        }
        long days = Math.round(hours / 24.0);
        if (days < 26) {
            return new TimeAmount(days, "일", "day");
        }
        if (days < 46) {
            return new TimeAmount(1, "개월", "month");
        }
        long months = Math.round(days / 30.0);
        if (days < 320) {
            return new TimeAmount(months, "개월", "month");
        }
        if (days < 548) {
            return new TimeAmount(1, "년", "year");
        }
        long years = Math.round(days / 365.0);
        return new TimeAmount(years, "년", "year");
    }

    private static boolean isKorean(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("ko");
    }

    private static class TimeAmount {
        private final long value;
        private final String koreanUnit;
        private final String englishUnit;

        private TimeAmount(long value, String koreanUnit, String englishUnit) {
            this.value = value;
            this.koreanUnit = koreanUnit;
            this.englishUnit = englishUnit;
        }
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

    /**
     * Show date string by two stage
     * which in a day and over a day
     */
    public static String socialDate(Date date){
        if (date == null) {
            return "";
        }
        DateTime dateTime = new DateTime(date);
        boolean isBeforeYesterday = dateTime.isBefore(DateTime.now().minusDays(1) );
        if(isBeforeYesterday){
            return dateTime.toString("yyyy-MM-dd h:mm a", Locale.getDefault());
        }

        return momentFromNow(date, Locale.getDefault().getLanguage());
    }

    public static String getDateStringWithoutSpace(Date date){
        if (date == null) {
            date = new Date();
        }
        DateTime dateTime = new DateTime(date);
        return dateTime.toString("yyyyMMddHHmm", Locale.getDefault());
    }

    public static String geYMDDate(Date date){
        if (date == null) {
            return "";
        }
        DateTime dateTime = new DateTime(date);
        return dateTime.toString("yyyy-MM-dd", Locale.getDefault());
    }

    public static String getOptionalShortDate(Date date){
        if (date == null) {
            return "";
        }
        DateTime targetTime = new DateTime(date);
        DateTime currentTime = new DateTime(new Date());

        if(isSameYear(targetTime, currentTime)) {
            if(isSameDay(targetTime, currentTime)) {
                return targetTime.toString("'at' h:mm a", Locale.getDefault());
            }
            return targetTime.toString("MMM d 'at' h:mm a", Locale.getDefault());
        } else {
            return targetTime.toString("YY.MM.dd 'at' h:mm a", Locale.getDefault());
        }
    }

    private static boolean isSameYear(DateTime targetTime, DateTime currentTime) {
        return currentTime.toString("YYYY").equals(targetTime.toString("YYYY"));
    }

    private static boolean isSameDay(DateTime targetTime, DateTime currentTime) {
        return currentTime.toString("YYYYMMdd").equals(targetTime.toString("YYYYMMdd"));
    }
}
