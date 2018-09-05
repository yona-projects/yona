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
