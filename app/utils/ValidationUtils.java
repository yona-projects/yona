package utils;

import play.mvc.Http;

/**
 * @author Keesun Baik
 */
public class ValidationUtils {

    public static void rejectIfEmpty(Http.Flash flash, String value, String message) {
        if(value == null || value.trim().isEmpty()) {
            flash.put(Constants.WARNING, message);
        }
    }
}
