/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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
package actions;

import actions.support.PathParser;
import controllers.annotation.IsOnlyGitAvailable;
import models.Project;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import utils.AccessLogger;
import utils.ErrorViews;

/**
 * 1. 프로젝트가 존재하는지 확인한다.
 * 2. 프로젝트가 Git 프로젝트인지 확인한다.
 *
 * @author Keesun Baik
 */
public class IsOnlyGitAvailableAction extends Action<IsOnlyGitAvailable> {

    @Override
    public Result call(Http.Context context) throws Throwable {
        PathParser parser = new PathParser(context);
        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        if (project == null) {
            return AccessLogger.log(context.request(), notFound(ErrorViews.NotFound.render("error.notfound.project"))
                    , null);
        }

        if(!project.isGit()) {
            return AccessLogger.log(context.request(), badRequest(ErrorViews.BadRequest.render("error.badrequest.only.available.for.git")), null);
        }

        return this.delegate.call(context);
    }

}
