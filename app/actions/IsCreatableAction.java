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

import models.Project;
import models.User;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Result;
import play.libs.F.Promise;
import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;
import actions.support.PathParser;
import controllers.UserApp;
import controllers.annotation.IsCreatable;

/**
 * After {@link actions.AbstractProjectCheckAction},
 * Validate whether the current user is allowed to create a resource of certain type in the project.
 *
 * @author Wansoon Park, Keesun Baik
 * @see {@link AbstractProjectCheckAction}
 * @see {@link controllers.annotation.IsCreatable}
 */
public class IsCreatableAction extends AbstractProjectCheckAction<IsCreatable> {
    @Override
    protected Promise<Result> call(Project project, Context context, PathParser parser) throws Throwable {
        User currentUser = UserApp.currentUser();
        if (!AccessControl.isProjectResourceCreatable(currentUser, project, this.configuration.value())) {
            Promise<Result> promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden", project)));
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }

        return this.delegate.call(context);
    }
}
