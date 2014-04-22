/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
package controllers;

import models.User;
import models.Watch;
import models.enumeration.Operation;
import models.resource.Resource;
import models.resource.ResourceParam;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

public class WatchApp extends Controller {
    public static Result watch(ResourceParam resourceParam) {
        User user = UserApp.currentUser();
        Resource resource = resourceParam.resource;

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot watch it.");
        }

        if (!AccessControl.isAllowed(user, resource, Operation.READ)) {
            return forbidden("You have no permission to watch it.");
        }

        Watch.watch(user, resource);

        return ok();
    }

    public static Result unwatch(ResourceParam resourceParam) {
        User user = UserApp.currentUser();
        Resource resource = resourceParam.resource;

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot unwatch it.");
        }

        Watch.unwatch(user, resource);

        return ok();
    }
}
