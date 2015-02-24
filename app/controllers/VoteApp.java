/*
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim
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

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.Issue;
import models.IssueComment;
import models.Project;
import models.User;
import models.enumeration.Operation;
import play.db.ebean.Transactional;
import play.mvc.*;
import utils.RouteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Controller which plays a role in voting in the issue.
 */
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
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
    @IsAllowed(Operation.READ)
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
    @IsAllowed(Operation.READ)
    public static Result unvote(String ownerName, String projectName, Long issueNumber) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        issue.removeVoter(UserApp.currentUser());

        Call call = routes.IssueApp.issue(ownerName, projectName, issueNumber);

        return redirect(call);
    }

    @Transactional
    @IsAllowed(Operation.READ)
    public static Result voteComment(String user, String project, Long number, Long commentId) {
        IssueComment issueComment = IssueComment.find.byId(commentId);
        if (issueComment == null) {
            return notFound("issue.comment.error.vote");
        }

        issueComment.addVoter(UserApp.currentUser());

        return redirect(RouteUtil.getUrl(issueComment));
    }

    @Transactional
    @IsAllowed(Operation.READ)
    public static Result unvoteComment(String user, String project, Long number, Long commentId) {
        IssueComment issueComment = IssueComment.find.byId(commentId);
        if (issueComment == null) {
            return notFound("issue.comment.error.unvote");
        }

        if (!issueComment.voters.contains(UserApp.currentUser())) {
            return notFound("issue.comment.error.have.not.voted");
        }

        issueComment.removeVoter(UserApp.currentUser());

        return redirect(RouteUtil.getUrl(issueComment));
    }

    public static List<User> getVotersForAvatar(Set<User> voters, int size){
        return getSubList(voters, 0, size);
    }

    public static List<User> getVotersForName(Set<User> voters, int fromIndex, int size){
        return getSubList(voters, fromIndex, fromIndex + size);
    }

    public static Set<User> getVotersExceptCurrentUser(Set<User> voters){
        voters.remove(UserApp.currentUser());
        return voters;
    }

    /**
     * Get subList of voters within its size.
     *
     * @param fromIndex
     * @param toIndex
     * @return
     */
    private static List<User> getSubList(Set<User> voters, int fromIndex, int toIndex) {
        try {
            return new ArrayList<>(voters).subList(
                    Math.max(0, fromIndex),
                    Math.min(voters.size(), toIndex)
            );
        } catch(IndexOutOfBoundsException e){
            play.Logger.warn("Failed to get subList of voters", e);
            return new ArrayList<User>();
        }
    }

}
