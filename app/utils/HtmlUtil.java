package utils;

import org.apache.commons.lang.StringUtils;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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
public class HtmlUtil {
    /**
     * Just plain default sanitize
     * eg. It is used when user change their name.
     * @param source
     * @return sanitized text
     */
    public static String defaultSanitize(String source){
        PolicyFactory policy = Sanitizers.FORMATTING;
        return  policy.sanitize(source);
    }

    public static String boolToCheckedString(boolean bool){
        if (bool == true) {
            return "checked";
        } else {
            return "";
        }
    }

    public static String boolToCheckedString(String bool){
        if (StringUtils.isBlank(bool)){
            return "";
        }
        if (bool.equals("true")) {
            return "checked";
        } else {
            return "";
        }
    }

}
