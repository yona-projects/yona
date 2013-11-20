/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park, Keesun Baek
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
package actions.support;

import org.apache.commons.lang3.StringUtils;


public class PathParser {
    private static final char DELIMETER = '/';
    private String[] paths;
    private String contextPath;
    private String path;

    public PathParser(String path) {
        this.paths = StringUtils.split(path, DELIMETER);
    }

    public PathParser(String contextPath, String path) {
        this.contextPath = contextPath;
        this.path = path;
        makePath();
    }

    private void makePath() {
        if(StringUtils.endsWith(this.contextPath, "/")) {
            int start = 0;
            int end = this.contextPath.length() - 1;
            this.contextPath = StringUtils.substring(this.contextPath, start, end);
        }
        this.path = StringUtils.remove(this.path, this.contextPath);
        this.paths = StringUtils.split(this.path, DELIMETER);
    }

    public String getOwnerLoginId() {
        return this.paths[0];
    }

    public String getProjectName() {
        return this.paths[1];
    }

    public int getPullRequestNumber() {
        return Integer.parseInt(this.paths[3]);
    }
    public String toString() {
        return DELIMETER + StringUtils.join(this.paths, DELIMETER);
    }
}
