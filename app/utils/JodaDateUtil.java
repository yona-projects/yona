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

}
