/*
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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

import models.Issue;
import play.mvc.Call;
import play.mvc.With;
import models.Project;
import play.mvc.Result;
import play.mvc.Controller;
import play.db.ebean.Transactional;
import actions.AnonymousCheckAction;
import models.enumeration.ResourceType;
import controllers.annotation.IsCreatable;

/**
 * The Controller which plays a role in voting in the issue.
 */
@With(AnonymousCheckAction.class)
public class VoteApp extends Controller {

    /**
     * Votes the issue.
     *
     * The vote of current user is added to the issue having {@code issueNumber}.
     *
     * @param ownerName
     * @param projectName
     * @param issueNumber
     * @return
     */
    @Transactional
    @IsCreatable(ResourceType.ISSUE_COMMENT)
    public static Result vote(String ownerName, String projectName, Long issueNumber) {

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        issue.addVoter(UserApp.currentUser());

        Call call = routes.IssueApp.issue(ownerName, projectName, issueNumber);

        return redirect(call);
    }

    /**
     * Cancels the vote.
     *
     * It is canceled that the vote of current user is in the issue having {@code issueNumber}.
     *
     * @param ownerName
     * @param projectName
     * @param issueNumber
     * @return
     */
    @Transactional
    @IsCreatable(ResourceType.ISSUE_COMMENT)
    public static Result unvote(String ownerName, String projectName, Long issueNumber) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        issue.removeVoter(UserApp.currentUser());

        Call call = routes.IssueApp.issue(ownerName, projectName, issueNumber);

        return redirect(call);
    }
}
