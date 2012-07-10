package utils;

import java.util.Date;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class JodaDateUtil {
	public static Date today() {
		return LocalDate.now().toDate();
	}

}
