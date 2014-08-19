package controllers.annotation;

import actions.AnonymousCheckAction;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 */
@With(AnonymousCheckAction.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AnonymousCheck {
    boolean requiresLogin() default false;
    boolean displaysFlashMessage() default false;
}
