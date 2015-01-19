/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park, Keesun Baek
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
import controllers.UserApp;
import models.Project;
import models.User;
import play.i18n.Messages;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Result;
import play.libs.F.Promise;
import utils.AccessLogger;
import utils.ErrorViews;

import static play.mvc.Controller.flash;

/**
 * Checks if the project which meets the request of a pattern,
 * /{user.loginId}/{project.name}/**, exits.
 * - If the project doesn't exist and current user has no permission to read, the response will be with 403 Forbidden.
 *
 * @author Keeun Baik
 */
public class NullProjectCheckAction extends Action<Void> {

    @Override
    public Promise<Result> call(Http.Context context) throws Throwable {
        PathParser parser = new PathParser(context);
        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        if (project == null) {
            Promise<Result> promise;

            if (UserApp.currentUser() == User.anonymous){
                flash("failed", Messages.get("error.auth.unauthorized.waringMessage"));
                promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())));
            } else {
                promise = Promise.pure((Result) forbidden(ErrorViews.NotFound.render("error.forbidden.or.notfound")));
            }

            AccessLogger.log(context.request(), promise, null);

            return promise;
        }

        return this.delegate.call(context);
    }
}
