/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author kjkmadness
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
import play.mvc.Result;
import play.libs.F.Promise;
import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;

import static play.mvc.Controller.flash;

/**
 * Checks if the project which meets the request of a pattern,
 * /{user.loginId}/{project.name}/**, exists.
 * - If the project doesn't exist and current user has no permission to read, the response will be with 403 Forbidden.
 * - If the project exists, execute additional validation will be executed
 * by calling {@link AbstractProjectCheckAction#call(models.Project, play.mvc.Http.Context, actions.support.PathParser)}.
 *
 * @author Keesun Baik, kjkmadness
 */
public abstract class AbstractProjectCheckAction<T> extends Action<T> {
    @Override
    public final Promise<Result> call(Context context) throws Throwable {
        PathParser parser = new PathParser(context);
        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        Promise<Result> promise;

        if (project == null) {
            if (UserApp.currentUser() == User.anonymous){
                flash("failed", Messages.get("error.auth.unauthorized.waringMessage"));
                promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())));
            } else {
                promise = Promise.pure((Result) forbidden(ErrorViews.NotFound.render("error.forbidden.or.notfound")));
            }

            AccessLogger.log(context.request(), promise, null);

            return promise;
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            flash("failed", Messages.get("error.auth.unauthorized.waringMessage"));
            promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())));
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }

        return call(project, context, parser);
    }

    protected abstract Promise<Result> call(Project project, Context context, PathParser parser)
            throws Throwable;
}
