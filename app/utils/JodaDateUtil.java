package utils;

import java.util.Date;

import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class JodaDateUtil {
    public static Date today() {
        return LocalDate.now().toDate();
    }

    public static Date now() {
        return DateTime.now().toDate();
    }

    public static Duration ago(DateTime time) {
        return new Duration(DateTime.now(), time);
    }
    
    public static Duration ago(Date time) {
        return new Duration(DateTime.now(), new DateTime(time));
    }

}
