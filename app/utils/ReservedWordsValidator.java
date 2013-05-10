package utils;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Constraints.Validator;
import play.libs.F.*;
import static play.libs.F.*;

/**
 * Reserved words Validator
 * 
 * @author kjkmadnesss
 */
public class ReservedWordsValidator extends Validator<String> {
    public static final String MESSAGE = "validation.reservedWord";
    public static final String[] RESERVED_WORDS = {"navitest", "assets", "messages.js", "favicon.ico", "init", "users",
        "user", "info", "memberinfo", "sites", "lostPassword", "resetPassword", "files", "tags", "projectform",
        "projects", "svn", "!svn-fake", "help"};

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