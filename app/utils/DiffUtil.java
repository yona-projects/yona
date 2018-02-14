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

        String oldVal = Optional.ofNullable(oldValue).orElse("");
        String newVal = Optional.ofNullable(newValue).orElse("");

        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = DIFF_EDITCOST;
        StringBuilder sb = new StringBuilder();

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldVal, newVal);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    String deleteStyle = "<span style='background-color: #fda9a6;padding: 2px 0;'>";
                    sb.append(addDiffStyle(diff, deleteStyle));
                    break;
                case EQUAL:
                    int textLength = diff.text.length();

                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(addHeadOfDiff(diff));
                        sb.append(addEllipsis());
                        sb.append(addTailOfDiff(diff));
                    } else {
                        sb.append(addAllDiff(diff));
                    }

                    break;
                case INSERT:
                    String insertStyle = "<span style='background-color: #abdd52;padding: 2px 0;'>";
                    sb.append(addDiffStyle(diff, insertStyle));
                    break;
                default:
                        break;
            }
        }

        return sb.toString().replaceAll("\n", "&nbsp<br/>\n");
    }

    public static String getDiffPlainText(String oldValue, String newValue) {

        String oldVal = Optional.ofNullable(oldValue).orElse("");
        String newVal = Optional.ofNullable(newValue).orElse("");

        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = DIFF_EDITCOST;
        StringBuilder sb = new StringBuilder();

        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldVal, newVal);
        dmp.diff_cleanupEfficiency(diffs);

        for (Diff diff: diffs) {
            switch (diff.operation) {
                case DELETE:
                    String deleteText = "--- ";
                    sb.append(addDiffText(diff, deleteText));
                    break;
                case EQUAL:
                    int textLength = diff.text.length();

                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(addHeadOfDiff(diff))
                                .append(addEllipsisText())
                                .append(addTailOfDiff(diff));
                    } else {
                        sb.append(addAllDiff(diff));
                    }

                    sb.append("\n");
                    break;
                case INSERT:
                    String insertText = "+++ ";
                    sb.append(addDiffText(diff, insertText));
                    break;
                default:
                    break;
            }
        }

        return sb.toString();
    }

    private static String addHeadOfDiff(Diff diff) {
        return StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE));
    }

    private static String addTailOfDiff(Diff diff) {
        return StringEscapeUtils.escapeHtml4(diff.text.substring(diff.text.length() - EQUAL_TEXT_BASE_SIZE));
    }

    private static String addAllDiff(Diff diff) {
        return StringEscapeUtils.escapeHtml4(diff.text);
    }

    private static String addEllipsis() {
        return "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...\n"
                + "......\n"
                + "......\n"
                + "...</span>";
    }

    private static String addDiffStyle(Diff diff, String style) {
        return style
                + StringEscapeUtils.escapeHtml4(diff.text)
                + "</span>";
    }

    private static String addDiffText(Diff diff, String text) {
        return text
                + StringEscapeUtils.escapeHtml4(diff.text)
                + "\n";
    }

    private static String addEllipsisText() {
        return "......\n"
                + "......\n"
                + "...\n";
    }
}
