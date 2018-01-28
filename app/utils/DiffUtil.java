/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import java.util.LinkedList;
import java.util.Optional;

import org.apache.commons.lang3.StringEscapeUtils;
import utils.diff_match_patch.Diff;

public class DiffUtil {

    public static final int EQUAL_TEXT_ELLIPSIS_SIZE = 100;
    public static final int EQUAL_TEXT_BASE_SIZE = 50;
    public static final int DIFF_EDITCOST = 8;

    public static String getDiffText(String oldValue, String newValue) {

        oldValue = Optional.ofNullable(oldValue).orElse("");
        newValue = Optional.ofNullable(newValue).orElse("");

        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = DIFF_EDITCOST;
        String diffString = "";

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldValue, newValue);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    diffString += "<span style='background-color: #fda9a6;padding: 2px 0;'>";
                    diffString += StringEscapeUtils.escapeHtml4(diff.text);
                    diffString += "</span>";
                    break;
                case EQUAL:
                    int textLength = diff.text.length();
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        diffString += StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE));

                        diffString += "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...\n";
                        diffString += "......\n";
                        diffString += "......\n";
                        diffString += "...</span>";

                        diffString += StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE));
                    } else {
                        diffString += StringEscapeUtils.escapeHtml4(diff.text);
                    }
                    break;
                case INSERT:
                    diffString += "<span style='background-color: #abdd52;padding: 2px 0;'>";
                    diffString += StringEscapeUtils.escapeHtml4(diff.text);
                    diffString += "</span>";
                    break;
                default:
                        break;
            }
        }

        return diffString.replaceAll("\n", "&nbsp<br/>\n");
    }

    public static String getDiffPlainText(String oldValue, String newValue) {

        oldValue = Optional.ofNullable(oldValue).orElse("");
        newValue = Optional.ofNullable(newValue).orElse("");

        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = DIFF_EDITCOST;
        String diffString = "";

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldValue, newValue);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    diffString += "--- ";
                    diffString += StringEscapeUtils.escapeHtml4(diff.text);
                    diffString += "\n";
                    break;
                case EQUAL:
                    int textLength = diff.text.length();
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        diffString += StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE));

                        diffString += "......\n";
                        diffString += "......\n";
                        diffString += "...\n";

                        diffString += StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE));
                        diffString += "\n";
                    } else {
                        diffString += StringEscapeUtils.escapeHtml4(diff.text);
                        diffString += "\n";
                    }
                    break;
                case INSERT:
                    diffString += "+++ ";
                    diffString += StringEscapeUtils.escapeHtml4(diff.text);
                    diffString += "\n";
                    break;
                default:
                    break;
            }
        }

        return diffString;
    }
}
