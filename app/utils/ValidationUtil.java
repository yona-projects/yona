package utils;

import play.Play;

import javax.validation.Validator;

public final class ValidationUtil {
    private ValidationUtil() {
    }

    public static Validator getValidator() {
        return Play.application().injector().instanceOf(Validator.class);
    }
}
