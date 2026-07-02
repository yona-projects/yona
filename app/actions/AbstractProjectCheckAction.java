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
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Result;
import java.util.concurrent.*;
import utils.*;

import utils.LegacyRequestContext;

/**
 * Checks if the project which meets the request of a pattern,
 * /{user.loginId}/{project.name}/**, exists.
 * - If the project doesn't exist and current user has no permission to read, the response will be with 403 Forbidden.
 * - If the project exists, execute additional validation will be executed
 * by calling {@link AbstractProjectCheckAction#call(models.Project, play.mvc.Http.Request, actions.support.PathParser)}.
 *
 * @author Keesun Baik, kjkmadness
 */
public abstract class AbstractProjectCheckAction<T> extends Action<T> {
    @Override
    public final CompletionStage<Result> call(Request request) {
        return LegacyRequestContext.withRequest(request, () -> {
            String ownerLoginId = null;
            String projectName = null;

            PathParser parser = new PathParser(request);
            PathVariable pathVariable = new PathVariable(request.path());
            if (pathVariable.isApiCall()) {
                // eg. request.path() : /-_-api/v1/owners/doortts/projects/Test/posts
                ownerLoginId = pathVariable.getPathVariable("owners");
                projectName = pathVariable.getPathVariable("projects");
            } else {
                ownerLoginId = parser.getOwnerLoginId();
                projectName = parser.getProjectName();
            }

            Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

            CompletionStage<Result> promise;

            if (project == null) {
                Project previousProject = Project.findByPreviousPlaceOf(ownerLoginId, projectName);
                if (previousProject != null) {
                    return RedirectUtil.redirect(previousProject);
                }

                if (UserApp.currentUser() == User.anonymous){
                    LegacyRequestContext.flash().put("failed", MessagesUtil.get("error.auth.unauthorized.waringMessage"));
                    promise = CompletableFuture.completedFuture((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", request.path())));
                } else {
                    promise = CompletableFuture.completedFuture((Result) forbidden(ErrorViews.NotFound.render("error.forbidden.or.notfound")));
                }

                AccessLogger.log(request, promise, null);

                return promise;
            }

            if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
                LegacyRequestContext.flash().put("failed", MessagesUtil.get("error.auth.unauthorized.waringMessage"));
                promise = CompletableFuture.completedFuture((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", request.path())));
                AccessLogger.log(request, promise, null);
                return promise;
            }

            return call(project, request, parser);
        });
    }

    protected abstract CompletionStage<Result> call(Project project, Request request, PathParser parser);
}
