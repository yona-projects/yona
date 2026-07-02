/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LobString {
    private static final Pattern H2_QUOTED_CLOB = Pattern.compile("^clob\\d+: '(.*)'$", Pattern.DOTALL);
    private static final Pattern H2_STRING_DECODE_CLOB = Pattern.compile("^clob\\d+: STRINGDECODE\\('(.*)'\\)$", Pattern.DOTALL);

    private LobString() {
    }

    public static String unwrap(String value) {
        if (value == null) {
            return null;
        }

        Matcher stringDecodeClob = H2_STRING_DECODE_CLOB.matcher(value);
        if (stringDecodeClob.matches()) {
            return decodeH2String(unescapeSqlString(stringDecodeClob.group(1)));
        }

        Matcher quotedClob = H2_QUOTED_CLOB.matcher(value);
        if (quotedClob.matches()) {
            return unescapeSqlString(quotedClob.group(1));
        }

        return value;
    }

    public static boolean needsUnwrap(String value) {
        return value != null && (H2_QUOTED_CLOB.matcher(value).matches() || H2_STRING_DECODE_CLOB.matcher(value).matches());
    }

    private static String unescapeSqlString(String value) {
        return value.replace("''", "'");
    }

    private static String decodeH2String(String value) {
        StringBuilder decoded = new StringBuilder(value.length());

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                decoded.append(current);
                continue;
            }

            char escaped = value.charAt(++index);
            switch (escaped) {
                case 'b':
                    decoded.append('\b');
                    break;
                case 't':
                    decoded.append('\t');
                    break;
                case 'n':
                    decoded.append('\n');
                    break;
                case 'f':
                    decoded.append('\f');
                    break;
                case 'r':
                    decoded.append('\r');
                    break;
                case 'u':
                    if (index + 4 < value.length()) {
                        String hex = value.substring(index + 1, index + 5);
                        try {
                            decoded.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            decoded.append("\\u");
                            break;
                        }
                    }
                    decoded.append("\\u");
                    break;
                default:
                    decoded.append(escaped);
                    break;
            }
        }

        return decoded.toString();
    }
}
