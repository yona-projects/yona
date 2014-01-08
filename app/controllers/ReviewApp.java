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
package controllers;

import actions.AnonymousCheckAction;
import controllers.annotation.ProjectAccess;
import controllers.annotation.PullRequestAccess;
import models.NotificationEvent;
import models.Project;
import models.PullRequest;
import models.PullRequestEvent;
import models.enumeration.EventType;
import models.enumeration.Operation;
import play.api.mvc.Call;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;

/**
 * @author Keesun Baik
 */
public class ReviewApp extends Controller {

    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.ACCEPT)
    public static Result review(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        pullRequest.addReviewer(UserApp.currentUser());

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, call, EventType.PULL_REQUEST_REVIEWED);

        return redirect(call);
    }

    @Transactional
    @With(AnonymousCheckAction.class)
    @ProjectAccess(Operation.READ)
    @PullRequestAccess(Operation.ACCEPT)
    public static Result unreview(String userName, String projectName, Long pullRequestNumber) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        pullRequest.removeReviewer(UserApp.currentUser());

        Call call = routes.PullRequestApp.pullRequest(userName, projectName, pullRequestNumber);
        addNotification(pullRequest, call, EventType.PULL_REQUEST_UNREVIEWED);

        return redirect(call);
    }

    private static void addNotification(PullRequest pullRequest, Call call, EventType eventType) {
        NotificationEvent notiEvent = NotificationEvent.afterReviewed(call, pullRequest, eventType);
        PullRequestEvent.addEvent(notiEvent, pullRequest);
    }

}
