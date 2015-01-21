/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Daegeun Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.Payload;

import play.data.validation.Constraints;
import play.libs.F;
import play.libs.F.Tuple;

public class ExConstraints {
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RestrictedValidator.class)
    @play.data.Form.Display(name="constraint.restricted")
    public static @interface Restricted {
        String message() default RestrictedValidator.message;
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String[] value();
        boolean ignoreCase() default false;
    }

    /**
     * Validator for <code>@Restricted</code> fields.
     */
    public static class RestrictedValidator extends Constraints.Validator<Object> implements ConstraintValidator<Restricted, Object> {
        public static final String message = "error.restricted";
        private String[] words;
        private boolean ignoreCase;

        public void initialize(Restricted constraintAnnotation) {
            this.words = constraintAnnotation.value();
            this.ignoreCase = constraintAnnotation.ignoreCase();
        }

        public boolean isValid(Object object) {
            if (object == null) {
                return false;
            }
            if (words == null) {
                return true;
            }
            String value = String.valueOf(object);
            for (String word : words) {
                if (!ignoreCase && value.equalsIgnoreCase(word)) {
                    return false;
                }
                if (ignoreCase && value.equals(word)) {
                    return false;
                }
            }
            return true;
        }

        public Tuple<String, Object[]> getErrorMessageKey() {
            return F.Tuple(message, new Object[] {});
        }
    }

}
