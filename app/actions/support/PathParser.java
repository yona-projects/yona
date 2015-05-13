/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park, Keesun Baik
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

import javax.annotation.Nonnull;

/**
 * Parse URLs related with Project.
 * Extracts required information from URL with specific pattern, /{user.loginId}/{project.name}/**.
 *
 * @author Wansoon Park
 * @author Keesun Baik
 */
public class PathParser {
    private static final String DELIM = "/";
    @Nonnull private final String[] pathSegments;

    public PathParser(String path) {
        this.pathSegments = StringUtils.split(path, DELIM);
        if (this.pathSegments == null) {
            throw new NullPointerException();
        }
    }

    public PathParser(String contextPath, String path) {
        String contextRemovedPath = StringUtils.removeStart(path, contextPath);
        this.pathSegments = StringUtils.split(contextRemovedPath, DELIM);
        if (this.pathSegments == null) {
            throw new NullPointerException();
        }
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
