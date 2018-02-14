/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package utils;

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

public class DiffUtilTest {

    String DIFF_DELETE_PREFIX = "<span style='background-color: #fda9a6;padding: 2px 0;'>";
    String DIFF_DELETE_POSTFIX = "</span>";

    String DIFF_INSERT_PREFIX = "<span style='background-color: #abdd52;padding: 2px 0;'>";
    String DIFF_INSERT_POSTFIX = "</span>";

    String DIFF_EQUAL_PREFIX = "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...&nbsp<br/>\n"
            + "......&nbsp<br/>\n"
            + "......&nbsp<br/>\n"
            + "...</span>";

    String DIFF_NEW_LINE = "\n";

    String DIFF_DELETE_PLAIN_PREFIX = "--- ";

    String DIFF_INSERT_PLAIN_PREFIX = "+++ ";

    String DIFF_EQUAL_PLAIN_PREFIX = "......\n"
            + "......\n"
            + "...\n";

    @Test
    public void getDiffText_oldValueIsNull_returnString() {

        // GIVEN
        String oldValue = null;
        String newValue = "new value";
        String expectedResult = DIFF_INSERT_PREFIX + newValue + DIFF_INSERT_POSTFIX;

        // WHEN
        String result = DiffUtil.getDiffText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffText_newValueIsNull_returnString() {

        // GIVEN
        String oldValue = "oldValue";
        String newValue = null;
        String expectedResult = DIFF_DELETE_PREFIX + oldValue + DIFF_DELETE_POSTFIX;

        // WHEN
        String result = DiffUtil.getDiffText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffText_equalValuesMoreThanSize100_returnString() {

        // GIVEN
        String oldValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890";
        String newValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890";
        int textLength = oldValue.length();
        String expectedResult = oldValue.substring(0, 50) + DIFF_EQUAL_PREFIX + oldValue.substring(textLength - 50);

        // WHEN
        String result = DiffUtil.getDiffText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffText_equalValuesLessThanSize100_returnString() {

        // GIVEN
        String oldValue = "12345678901234567890";
        String newValue = "12345678901234567890";
        String expectedResult = oldValue;

        // WHEN
        String result = DiffUtil.getDiffText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffText_deleteAndInsertValue_returnString() {

        // GIVEN
        String oldValue = "Hi, there?";
        String newValue = "Hello, mijeong?";
        String expectedResult = oldValue.substring(0, 1)
                + DIFF_DELETE_PREFIX + oldValue.substring(1, oldValue.length() - 1) + DIFF_DELETE_POSTFIX
                + DIFF_INSERT_PREFIX + newValue.substring(1, newValue.length() - 1) + DIFF_DELETE_POSTFIX
                + oldValue.substring(oldValue.length() - 1);

        // WHEN
        String result = DiffUtil.getDiffText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffPlainText_oldValueIsNull_returnString() {

        // GIVEN
        String oldValue = null;
        String newValue = "new value";
        String expectedResult = DIFF_INSERT_PLAIN_PREFIX + newValue + DIFF_NEW_LINE;

        // WHEN
        String result = DiffUtil.getDiffPlainText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffPlainText_newValueIsNull_returnString() {

        // GIVEN
        String oldValue = "oldValue";
        String newValue = null;
        String expectedResult = DIFF_DELETE_PLAIN_PREFIX + oldValue + DIFF_NEW_LINE;

        // WHEN
        String result = DiffUtil.getDiffPlainText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffPlainText_equalValuesMoreThanSize100_returnString() {

        // GIVEN
        String oldValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890";
        String newValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890";
        int textLength = oldValue.length();
        String expectedResult = oldValue.substring(0, 50) + DIFF_EQUAL_PLAIN_PREFIX
                + oldValue.substring(textLength - 50) + DIFF_NEW_LINE;

        // WHEN
        String result = DiffUtil.getDiffPlainText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffPlainText_equalValuesLessThanSize100_returnString() {

        // GIVEN
        String oldValue = "12345678901234567890";
        String newValue = "12345678901234567890";
        String expectedResult = oldValue + DIFF_NEW_LINE;

        // WHEN
        String result = DiffUtil.getDiffPlainText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDiffPlainText_deleteAndInsertValue_returnString() {

        // GIVEN
        String oldValue = "Hi, there?";
        String newValue = "Hello, mijeong?";
        String expectedResult = oldValue.substring(0, 1) + DIFF_NEW_LINE
                + DIFF_DELETE_PLAIN_PREFIX + oldValue.substring(1, oldValue.length() - 1) + DIFF_NEW_LINE
                + DIFF_INSERT_PLAIN_PREFIX + newValue.substring(1, newValue.length() - 1) + DIFF_NEW_LINE
                + oldValue.substring(oldValue.length() - 1) + DIFF_NEW_LINE;

        // WHEN
        String result = DiffUtil.getDiffPlainText(oldValue, newValue);

        // THEN
        assertThat(result).isEqualTo(expectedResult);
    }
}
