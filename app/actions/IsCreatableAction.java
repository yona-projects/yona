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
package actions;

import models.Project;
import models.User;
import play.mvc.Action;
import play.mvc.Http.Context;
import play.mvc.Result;
import utils.AccessControl;
import utils.ErrorViews;
import actions.support.PathParser;
import controllers.UserApp;
import controllers.annotation.IsCreatable;

/**
 * 해당 프로젝트가 존재하는지 체크하고 사용자가 resource type별 생성권한이 있는지 체크한다.
 *
 * @author Wansoon Park, Keesun Beak
 *
 */
public class IsCreatableAction extends Action<IsCreatable> {

    @Override
    public Result call(Context context) throws Throwable {
        String path = context._requestHeader().path();

        play.Configuration config = play.Configuration.root();
        String contextPath = config.getString("application.context");

        PathParser parser = new PathParser(contextPath, path);

        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        if (project == null) {
            return notFound(ErrorViews.NotFound.render("No project matches given parameters '" + ownerLoginId + "' and project_name '" + projectName + "'"));
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isProjectResourceCreatable(currentUser, project, this.configuration.value())) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        return this.delegate.call(context);
    }

}
