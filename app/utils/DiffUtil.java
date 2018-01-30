/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
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
        StringBuilder sb = new StringBuilder();

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldValue, newValue);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    sb.append("<span style='background-color: #fda9a6;padding: 2px 0;'>")
                        .append(StringEscapeUtils.escapeHtml4(diff.text))
                        .append("</span>");
                    break;
                case EQUAL:
                    int textLength = diff.text.length();
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE)))
                            .append("<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...\n")
                            .append("......\n")
                            .append("......\n")
                            .append("...</span>")
                            .append(StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE)));
                    } else {
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text));
                    }
                    break;
                case INSERT:
                    sb.append("<span style='background-color: #abdd52;padding: 2px 0;'>")
                        .append(StringEscapeUtils.escapeHtml4(diff.text))
                        .append("</span>");
                    break;
                default:
                        break;
            }
        }

        return sb.toString().replaceAll("\n", "<br/>\n");
    }

    public static String getDiffPlainText(String oldValue, String newValue) {

        oldValue = Optional.ofNullable(oldValue).orElse("");
        newValue = Optional.ofNullable(newValue).orElse("");

        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = DIFF_EDITCOST;
        StringBuilder sb = new StringBuilder();

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldValue, newValue);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    sb.append("--- ")
                        .append(StringEscapeUtils.escapeHtml4(diff.text))
                        .append("\n");
                    break;
                case EQUAL:
                    int textLength = diff.text.length();
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE)))
                            .append("......\n")
                            .append("......\n")
                            .append("...\n")
                            .append(StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE)))
                            .append("\n");
                    } else {
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text))
                            .append("\n");
                    }
                    break;
                case INSERT:
                    sb.append("+++ ")
                        .append(StringEscapeUtils.escapeHtml4(diff.text))
                        .append("\n");
                    break;
                default:
                    break;
            }
        }

        return sb.toString();
    }
}
