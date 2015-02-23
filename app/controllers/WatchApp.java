/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import com.avaje.ebean.annotation.Transactional;
import models.Unwatch;
import models.User;
import models.Watch;
import models.enumeration.Operation;
import models.resource.Resource;
import models.resource.ResourceParam;
import play.mvc.Controller;
import play.mvc.Result;
import play.i18n.Messages;
import utils.AccessControl;
import utils.HttpUtil;
import utils.RouteUtil;
import org.apache.commons.lang3.StringUtils;

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

    @Transactional
    public static Result unwatch(ResourceParam resourceParam) {
        User user = UserApp.currentUser();
        Resource resource = resourceParam.resource;

        if (user.isAnonymous()) {
            return forbidden(views.html.error.forbidden.render(Messages.get("issue.error.unwatch.anonymous"), resource.getProject()));
        }

        if (!AccessControl.isAllowed(user, resource, Operation.READ)) {
            return forbidden(views.html.error.forbidden.render(Messages.get("issue.error.unwatch.permission"), resource.getProject()));
        }

        Unwatch unwatch = Unwatch.findBy(user, resource.getType(), resource.getId());
        Watch.unwatch(user, resource);

        if (HttpUtil.isJSONPreferred(request())) {
            return ok();
        } else {
            if (unwatch == null) {
                String message = getUnwatchMessage(resource);

                if (!StringUtils.isEmpty(message)) {
                    flash(utils.Constants.SUCCESS, message);
                }
            }

            return redirect(RouteUtil.getUrl(resource.getType(), resource.getId()));
        }
    }

    private static String getUnwatchMessage(Resource resource){
        switch(resource.getType()) {
            case ISSUE_POST:
            case ISSUE_COMMENT:
                return Messages.get("issue.unwatch.start");
            case BOARD_POST:
            case NONISSUE_COMMENT:
                return Messages.get("post.unwatch.start");
            case PULL_REQUEST:
            case REVIEW_COMMENT:
                return Messages.get("pullRequest.unwatch.start");
            case PROJECT:
                return Messages.get("project.unwatch.start");
            default:
                return "";
        }
    }
}
