/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park, Keesun Baik
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
import play.mvc.Http;

/**
 * 프로젝트 관련 URL을 파싱한다.
 * /{user.loginId}/{project.name}/** 패턴에 해당하는 URL에서 프로젝트 owner와 name 정보를 축출한다.
 */
public class PathParser {
    private static final String DELIMETER = "/";
    private String[] paths;

    public PathParser(String path) {
        this.paths = StringUtils.split(path, DELIMETER);
    }

    public PathParser(String contextPath, String path) {
        String delimRemovedPath = StringUtils.removeEnd(contextPath, DELIMETER);
        String contextRemovedPath = StringUtils.removeStart(path, delimRemovedPath);
        this.paths = StringUtils.split(contextRemovedPath, DELIMETER);
    }

    public PathParser(Http.Context context) {
        this(play.Configuration.root().getString("application.context"), context.request().path());
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
