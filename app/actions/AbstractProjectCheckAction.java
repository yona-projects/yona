/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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

import controllers.UserApp;
import models.Project;
import models.User;
import models.enumeration.Operation;
import actions.support.PathParser;
import play.i18n.Messages;
import play.mvc.Action;
import play.mvc.Http.Context;
import play.mvc.Result;
import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;

import static play.mvc.Controller.flash;

/**
 * /{user.loginId}/{project.name}/** 패턴의 요청에 해당하는 프로젝트가 존재하는지 확인하는 액션.
 * - URL에 해당하는 프로젝트가 없을 때 404 Not Found 로 응답한다.
 * - 현재 사용자가 URL에 해당하는 프로젝트에 읽기 권한이 없을 경우 404 Not Found 로 응답한다.
 * - URL에 해당하는 프로젝트가 있을 때 {@link AbstractProjectCheckAction#call(Project, Context)} 을
 *   호출하여 이후 검증 과정을 수행한다.
 *
 * @author Keesun Baik, kjkmadness
 */
public abstract class AbstractProjectCheckAction<T> extends Action<T> {
    @Override
    public final Result call(Context context) throws Throwable {
        PathParser parser = new PathParser(context);
        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        if (project == null) {
            if (UserApp.currentUser() == User.anonymous){
                flash("failed", Messages.get("error.auth.unauthorized.waringMessage"));
                return AccessLogger.log(context.request(),
                        forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())), null);
            }
            return AccessLogger.log(context.request(),
                    forbidden(ErrorViews.NotFound.render("error.forbidden.or.notfound")), null);
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            flash("failed", Messages.get("error.auth.unauthorized.waringMessage"));
            return AccessLogger.log(context.request(),
                    forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())), null);
        }

        return call(project, context, parser);
    }

    protected abstract Result call(Project project, Context context, PathParser parser)
            throws Throwable;
}
