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
 * /{user.loginId}/{project.name}/** 패턴에 해당하는 URL에서 필요한 정보를 축출한다.
 *
 * @author Wansoon Park, Keesun Baik
 */
public class PathParser {
    private static final String DELIM = "/";
    private String[] pathSegments;

    public PathParser(String path) {
        this.pathSegments = StringUtils.split(path, DELIM);
    }

    public PathParser(String contextPath, String path) {
        String contextRemovedPath = StringUtils.removeStart(path, contextPath);
        this.pathSegments = StringUtils.split(contextRemovedPath, DELIM);
    }

    public PathParser(Http.Context context) {
        this(play.Configuration.root().getString("application.context"), context.request().path());
    }

    public String getOwnerLoginId() {
        return this.pathSegments[0];
    }

    public String getProjectName() {
        return this.pathSegments[1];
    }

    public String getPathSegment(int index) {
        return pathSegments[index];
    }

    public String toString() {
        return DELIM + StringUtils.join(this.pathSegments, DELIM);
    }

}
