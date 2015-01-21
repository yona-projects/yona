/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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
import controllers.annotation.IsAllowed;
import models.Project;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Result;
import play.libs.F.Promise;
import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;

/**
 * After {@link actions.AbstractProjectCheckAction},
 * 1. Check if the resource of the certain type exists.
 * 2. Check if the current user is permitted to specified operation on the resource.
 *
 * For more specific, see {@link controllers.annotation.IsAllowed}.
 *
 * @author Keesun Baik
 * @see {@link AbstractProjectCheckAction}
 * @see {@link controllers.annotation.IsAllowed}
 */
public class IsAllowedAction extends AbstractProjectCheckAction<IsAllowed> {
    @Override
    protected Promise<Result> call(Project project, Context context, PathParser parser) throws Throwable {
        ResourceType resourceType = this.configuration.resourceType();
        ResourceConvertible resourceObject = Resource.getResourceObject(parser, project, resourceType);
        Operation operation = this.configuration.value();

        if(resourceObject == null) {
            Promise<Result> promise = Promise.pure((Result) notFound(ErrorViews.NotFound.render("error.notfound", project, resourceType.resource())));
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }

        if(!AccessControl.isAllowed(UserApp.currentUser(), resourceObject.asResource(), operation)) {
            Promise<Result> promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden", project)));
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }

        return this.delegate.call(context);
    }
}
