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
import controllers.UserApp;
import controllers.annotation.IsAllowed;
import models.Project;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import utils.AccessControl;
import utils.AccessLogger;
import utils.ErrorViews;

/**
 * 1. 프로젝트가 존재하는지 확인한다.
 * 2. 특정 타입의 리소스가 존재하는지 확인한다.
 * 3. 특정 타입의 리소스에 권한이 있는지 확인한다.
 *
 * 자세한 설명은 {@link controllers.annotation.IsAllowed} 애노테이션 참고.
 *
 * @author Keesun Baik
 * @see {@link controllers.annotation.IsAllowed}
 */
public class IsAllowedAction extends Action<IsAllowed> {

    @Override
    public Result call(Http.Context context) throws Throwable {
        PathParser parser = new PathParser(context);
        String ownerLoginId = parser.getOwnerLoginId();
        String projectName = parser.getProjectName();

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);

        if (project == null) {
            return AccessLogger.log(context.request(),
                    notFound(ErrorViews.NotFound.render("error.notfound.project")), null);
        }

        ResourceType resourceType = this.configuration.resourceType();
        ResourceConvertible resourceObject = Resource.getResourceObject(parser, project, resourceType);
        Operation operation = this.configuration.value();

        if(resourceObject == null) {
            return AccessLogger.log(context.request(),
                    notFound(ErrorViews.NotFound.render("error.notfound", project, resourceType.resource())) , null);
        }

        if(!AccessControl.isAllowed(UserApp.currentUser(), resourceObject.asResource(), operation)) {
            return AccessLogger.log(context.request(),
                    forbidden(ErrorViews.Forbidden.render("error.forbidden", project)), null);
        }

        return this.delegate.call(context);
    }

}
