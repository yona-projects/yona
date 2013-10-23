package utils;

import org.joda.time.*;

import java.util.*;

public class JodaDateUtil {
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
        JSInvocable moment = MomentUtil.newMoment(time);
        return moment.invoke("fromNow");
    }

    public static String momentFromNow(Date time) {
        JSInvocable moment = MomentUtil.newMoment(time.getTime());
        return moment.invoke("fromNow");
    }

}
