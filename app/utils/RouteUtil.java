/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

package utils;

import controllers.CodeHistoryApp;
import models.*;
import models.enumeration.ResourceType;
import models.resource.Resource;

public class RouteUtil {
    public static String getUrl(ResourceType resourceType, String resourceId) {
        Long longId = Long.valueOf(resourceId);

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
            case PULL_REQUEST_COMMENT:
                return getUrl(PullRequestComment.find.byId(longId));
            default:
                throw new IllegalArgumentException(
                        Resource.getInvalidResourceTypeMessage(resourceType));
        }
    }

    public static String getUrl(Issue issue) {
        return controllers.routes.IssueApp.issue(
                issue.project.owner, issue.project.name, issue.getNumber()).url();
    }

    public static String getUrl(Posting post) {
        return controllers.routes.BoardApp.post(
                post.project.owner, post.project.name, post.getNumber()).url();
    }

    public static String getUrl(IssueComment comment) {
        return getUrl(comment.issue) + "#comment-" + comment.id;
    }

    public static String getUrl(PostingComment comment) {
        return getUrl(comment.posting) + "#comment-" + comment.id;
    }

    public static String getUrl(PullRequest pullRequest) {
        Project toProject = pullRequest.toProject;
        return controllers.routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).url();
    }

    public static String getUrl(CommitComment comment) {
        play.mvc.Call toView = controllers.routes.CodeHistoryApp.show(
                comment.project.owner, comment.project.name, comment.commitId);
        toView = CodeHistoryApp.backToThePullRequestCommitView(toView);
        return toView + "#comment-" + comment.id;
    }

    public static String getUrl(Comment comment) {
        if (comment instanceof IssueComment) {
            return getUrl((IssueComment) comment);
        } else if (comment instanceof PostingComment) {
            return getUrl((PostingComment) comment);
        }

        throw new IllegalArgumentException();
    }

    public static String getUrl(PullRequestComment newComment) {
        return getUrl(newComment.pullRequest) + "#comment-" + newComment.id;
    }
}
