/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

package utils;

import controllers.CodeHistoryApp;
import controllers.routes;
import models.*;
import models.enumeration.ResourceType;
import models.resource.Resource;

import models.SimpleCommentThread;
import models.NonRangedCodeCommentThread;
import models.CodeCommentThread;

import playRepository.Commit;
import utils.TemplateHelper.DiffRenderer$;

public class RouteUtil {
    public static final DiffRenderer$ diffRenderer = new DiffRenderer$();

    public static String getUrl(ResourceType resourceType, String resourceId) {
        Long longId = Long.valueOf(resourceId);

        try {
            switch(resourceType) {
                case ISSUE_POST:
                    return getUrl(Issue.finder.byId(longId));
                case ISSUE_COMMENT:
                    return getUrl(IssueComment.find.byId(longId));
                case NONISSUE_COMMENT:
                    return getUrl(PostingComment.find.byId(longId));
                case BOARD_POST:
                    return getUrl(Posting.finder.byId(longId));
                case COMMIT_COMMENT:
                    return getUrl(CommitComment.find.byId(longId));
                case PULL_REQUEST:
                    return getUrl(PullRequest.finder.byId(longId));
                case REVIEW_COMMENT:
                    return getUrl(ReviewComment.find.byId(longId));
                case COMMENT_THREAD:
                    return getUrl(CommentThread.find.byId(longId));
                default:
                    throw new IllegalArgumentException(
                            Resource.getInvalidResourceTypeMessage(resourceType));
            }
        } catch (Exception e) {
            play.Logger.error("Failed to get a url to the resource", e);
        }

        return null;
    }

    public static String getUrl(User user) {
        if (user == null) return null;
        user.refresh();

        return controllers.routes.UserApp.userInfo(
                user.loginId,
                controllers.routes.UserApp.userInfo$default$2(),
                controllers.routes.UserApp.userInfo$default$3(),
                controllers.routes.UserApp.userInfo$default$4()
        ).url();
    }

    public static String getUrl(Organization org) {
        if (org == null) return null;
        org.refresh();

        return controllers.routes.OrganizationApp.organization(org.name).url();
    }

    public static String getUrl(Project project) {
        if (project == null) return null;
        project.refresh();

        return controllers.routes.ProjectApp.project(project.owner, project.name).url();
    }

    public static String getUrl(Issue issue) {
        if (issue == null) return null;
        issue.refresh();

        return controllers.routes.IssueApp.issue(
                issue.project.owner, issue.project.name, issue.getNumber()).url();
    }

    public static String getUrl(Posting post) {
        if (post == null) return null;
        post.refresh();

        return controllers.routes.BoardApp.post(
                post.project.owner, post.project.name, post.getNumber()).url();
    }

    public static String getUrl(IssueComment comment) {
        if (comment == null) return null;

        return getUrl(comment.issue) + "#comment-" + comment.id;
    }

    public static String getUrl(PostingComment comment) {
        if (comment == null) return null;

        return getUrl(comment.posting) + "#comment-" + comment.id;
    }

    public static String getUrl(PullRequest pullRequest) {
        if (pullRequest == null) return null;

        Project toProject = pullRequest.toProject;
        return controllers.routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).url();
    }

    public static String getUrl(CommitComment comment) {
        if (comment == null) return null;

        play.mvc.Call toView = controllers.routes.CodeHistoryApp.show(
                comment.project.owner, comment.project.name, comment.commitId);
        return toView + "#comment-" + comment.id;
    }

    public static String getUrl(Comment comment) {
        if (comment == null) return null;

        if (comment instanceof IssueComment) {
            return getUrl((IssueComment) comment);
        } else if (comment instanceof PostingComment) {
            return getUrl((PostingComment) comment);
        }

        throw new IllegalArgumentException();
    }

    public static String getUrl(ReviewComment comment) {
        if (comment == null) return null;

        CommentThread thread = comment.thread;

        return diffRenderer.urlToContainer(thread) + "#comment-" + comment.id;
    }

    public static String getUrl(CommentThread thread) {
        return diffRenderer.urlToContainer(thread) + "#thread-" + thread.id;
    }

    public static String getUrl(Commit commit, Project project) {
        if (commit == null) return null;

        return controllers.routes.CodeHistoryApp.show(project.owner, project.name, commit.getId()).url();
    }
}
