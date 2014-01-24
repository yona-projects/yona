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
            format = "yyyy-MM-dd hh:mm:ss";
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

}
