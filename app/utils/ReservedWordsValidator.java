package utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.User;

import org.apache.commons.lang.StringUtils;

import play.api.Play;
import play.data.validation.Constraints.Validator;
import play.libs.Scala;
import play.libs.F.*;
import scala.Tuple3;
import static play.libs.F.*;

/**
 * Reserved words Validator
 * 
 * @author kjkmadnesss
 */
public class ReservedWordsValidator extends Validator<String> {
    public static final String MESSAGE = "validation.reservedWord";
    public static final Set<String> RESERVED_WORDS;

    static {
        RESERVED_WORDS = new HashSet<String>();
        List<Tuple3<String, String, String>> list = Scala.asJava(Play.current().routes().get().documentation());
        Pattern pattern = Pattern.compile("^/(" + User.LOGIN_ID_PATTERN + ")/?");
        for (Tuple3<String, String, String> tuple : list) {
            Matcher matcher = pattern.matcher(tuple._2());
            if (matcher.find()) {
                RESERVED_WORDS.add(matcher.group(1));
            }
        }
    }

    /**
     * get error message key
     * 
     * @return errorMessageKey
     * @see play.data.validation.Constraints.Validator#getErrorMessageKey()
     */
    @Override
    public Tuple<String, Object[]> getErrorMessageKey() {
        return Tuple(MESSAGE, new Object[] {});
    }

    /**
     * check the input string is valid or not
     * 
     * @param string input string
     * @return true if the input string is not a reserved word; false otherwise
     * @see play.data.validation.Constraints.Validator#isValid(java.lang.Object)
     */
    @Override
    public boolean isValid(String string) {
        return !isReserved(string);
    }

    /**
     * check the input string is a reserved word or not
     * 
     * @param string input string
     * @return true if the input string is a reserved word; false otherwise
     */
    public static boolean isReserved(String string) {
        for (String word : RESERVED_WORDS) {
            if (StringUtils.equalsIgnoreCase(string, word)) {
                return true;
            }
        }
        return false;
    }
}
