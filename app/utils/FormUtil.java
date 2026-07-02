package utils;

import play.Play;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FormUtil {
    private FormUtil() {
    }

    public static <T> Form<T> form(Class<T> clazz) {
        return Play.application().injector().instanceOf(FormFactory.class).form(clazz)
                .withDirectFieldAccess(true);
    }

    public static DynamicForm form() {
        return Play.application().injector().instanceOf(FormFactory.class).form();
    }

    public static void reject(Form<?> form, String error) {
        form.errors().add(new ValidationError("", error));
    }

    public static void reject(Form<?> form, String key, String error) {
        form.errors().add(new ValidationError(key, error));
    }

    public static boolean hasError(Form<?> form, String key) {
        return form.error(key).isPresent();
    }

    public static List<ValidationError> errors(Form<?> form, String key) {
        return form.errors(key);
    }

    public static Set<String> errorKeys(Form<?> form) {
        return form.errors().stream()
                .map(ValidationError::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String errorMessage(Form<?> form, String key) {
        return form.error(key).map(ValidationError::message).orElse("");
    }

    public static List<String> errorMessages(Form<?> form, String key) {
        return form.error(key).map(ValidationError::messages).orElseGet(java.util.Collections::emptyList);
    }

    public static void removeErrors(Form<?> form, String key) {
        form.errors().removeIf(error -> error.key().equals(key));
    }
}
