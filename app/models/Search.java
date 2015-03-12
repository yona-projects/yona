/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;
import com.avaje.ebean.Page;
import models.enumeration.Operation;
import models.enumeration.ProjectScope;
import models.enumeration.UserState;
import utils.AccessControl;

import java.util.ArrayList;
import java.util.List;

public class Search {

    private static final String DEFAULT_PATH_TO_PROJECT = "project";
    private static final String DEFAULT_PATH_TO_AUTHOR = "authorId";

    private static JunctionOperation<Issue> containsKeywordInIssue = new JunctionOperation<Issue>() {
        @Override
        public void withJunction(String keyword, Junction<Issue> junction) {
            containsKeywordIn(keyword, junction, new String[]{"title", "body"});
        }
    };

    private static JunctionOperation<Posting> containsKeywordInPosting = new JunctionOperation<Posting>() {
        @Override
        public void withJunction(String keyword, Junction<Posting> junction) {
            containsKeywordIn(keyword, junction, new String[]{"title", "body"});
        }
    };

    private static JunctionOperation<Milestone> containsKeywordInMilestone = new JunctionOperation<Milestone>() {
        @Override
        public void withJunction(String keyword, Junction<Milestone> junction) {
            containsKeywordIn(keyword, junction, new String[]{"title", "contents"});
        }
    };

    private static JunctionOperation<IssueComment> containsKeywordInIssueComment = new JunctionOperation<IssueComment>() {
        @Override
        public void withJunction(String keyword, Junction<IssueComment> junction) {
            containsKeywordIn(keyword, junction, new String[]{"contents"});
        }
    };

    private static JunctionOperation<PostingComment> containsKeywordInPostComment = new JunctionOperation<PostingComment>() {
        @Override
        public void withJunction(String keyword, Junction<PostingComment> junction) {
            containsKeywordIn(keyword, junction, new String[]{"contents"});
        }
    };

    private static JunctionOperation<ReviewComment> containsKeywordInReviewComment = new JunctionOperation<ReviewComment>() {
        @Override
        public void withJunction(String keyword, Junction<ReviewComment> junction) {
            containsKeywordIn(keyword, junction, new String[]{"contents"});
        }
    };

    /**
     * Finds all issues that a {@code user} can see.
     * - anonymous: find from only public project's issues.
     * - logged in user: find from
     *  - public projects
     *  - private projects that the user is member or admin of the project
     *  - protected projects that the user is member or admin of a group which has the project
     *
     * TODO sorting(default, desc createdDate)
     *
     * @param keyword
     * @param user
     * @param pageParam
     * @return
     */
    public static Page<Issue> findIssues(String keyword, User user, PageParam pageParam) {
        return issuesEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findIssues(String, User, PageParam)}
     *
     * @param keyword
     * @param user
     * @return
     */
    public static int countIssues(String keyword, User user) {
        return issuesEL(keyword, user).findRowCount();
    }

    /**
     * (Project && Keyword) || (Author && Keyword) || (Assignee && Keyword)
     *
     * @param keyword
     * @param user
     * @return
     */
    private static ExpressionList<Issue> issuesEL(String keyword, User user) {
        ExpressionList<Issue> el = Issue.finder.where();
        Junction<Issue> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInIssue);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInIssue);
        equalsUserTemplate(keyword, user, junction, "assignee.user.id", containsKeywordInIssue);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all issues in a {@code project} that a {@code user} can see
     * - If the project is public, then all user including anonymous can find by {@code keyword}
     * - If the project is private, keyword searching is available when
     * the {@code user} is member or admin of the {@code project}.
     * - If the project id protected, keyword searching is available when
     * the {@code user} is member or admin of the group of the {@code project}.
     * - Or, the issues created by the user.
     *
     * @param keyword
     * @param user
     * @param project
     * @param pageParam
     * @return
     */
    public static Page<Issue> findIssues(String keyword, User user, Project project, PageParam pageParam) {
        return issuesEL(keyword, user, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findIssues(String, User, Project, PageParam)}
     *
     * @param keyword
     * @param user
     * @param project
     * @return
     */
    public static int countIssues(String keyword, User user, Project project) {
        ExpressionList<Issue> el = issuesEL(keyword, user, project);
        return el.findRowCount();
    }

    private static ExpressionList<Issue> issuesEL(String keyword, User user, Project project) {
        ExpressionList<Issue> el = Issue.finder.where().eq("project", project);
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            Junction<Issue> junction = el.disjunction();
            junction.add(Expr.eq("authorId", user.id));
            junction.add(Expr.eq("assignee.user.id", user.id));
            junction.endJunction();
        }
        containsKeywordIn(keyword, el.conjunction(), new String[]{"title", "body"});
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all issues in a {@code organization} that a {@code user} can see
     * - If the project is public, then all user including anonymous can find by {@code keyword}
     * - If the project is private, keyword searching is available when
     * the {@code user} is member or admin of the {@code project}.
     * - If the project is protected, keyword searching is available to
     * the project's members and the organization's members
     *
     * @param keyword
     * @param user
     * @param organization
     * @param pageParam
     * @return
     */
    public static Page<Issue> findIssues(String keyword, User user, Organization organization, PageParam pageParam) {
        return issuesEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findIssues(String, User, Organization, PageParam)}
     *
     * @param keyword
     * @param user
     * @param organization
     * @return
     */
    public static int countIssues(String keyword, User user, Organization organization) {
        ExpressionList<Issue> el = issuesEL(keyword, user, organization);
        return el.findRowCount();
    }

    private static ExpressionList<Issue> issuesEL(String keyword, User user, Organization organization) {
        ExpressionList<Issue> el = Issue.finder.where()
                .eq("project.organization", organization);
        Junction<Issue> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInIssue);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInIssue);
        equalsUserTemplate(keyword, user, junction, "assignee.user.id", containsKeywordInIssue);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all posts that a {@code user} can see.
     * - anonymous: find from only public project's posts.
     * - logged in user: find from
     *  - public projects
     *  - private projects that the user is member or admin of the project
     *  - protected projects that the user is member or admin of a group which has the project
     *
     * @param keyword
     * @param user
     * @param pageParam
     * @return
     */
    public static Page<Posting> findPosts(String keyword, User user, PageParam pageParam) {
        return postsEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findPosts(String, User, PageParam)}
     *
     * @param keyword
     * @param user
     * @return
     */
    public static int countPosts(String keyword, User user) {
        return postsEL(keyword, user).findRowCount();
    }

    private static ExpressionList<Posting> postsEL(String keyword, User user) {
        ExpressionList<Posting> el = Posting.finder.where();
        Junction<Posting> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInPosting);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInPosting);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all posts in a {@code project} that a {@code user} can see
     * - If the project is public, then all user including anonymous can find by {@code keyword}
     * - If the project is private, keyword searching is available when
     * the {@code user} is member or admin of the {@code project}.
     * - If the project is protected, keyword searching is available to
     * the project's members and the organization's members
     *
     * @param keyword
     * @param user
     * @param project
     * @param pageParam
     * @return
     */
    public static Page<Posting> findPosts(String keyword, User user, Project project, PageParam pageParam) {
        return postsEL(keyword, user, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findPosts(String, User, Project, PageParam)}
     *
     * @param keyword
     * @param user
     * @param project
     * @return
     */
    public static int countPosts(String keyword, User user, Project project) {
        return postsEL(keyword, user, project).findRowCount();
    }

    private static ExpressionList<Posting> postsEL(String keyword, User user, Project project) {
        ExpressionList<Posting> el = Posting.finder.where()
                .eq("project", project);
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            el.eq("authorId", user.id);
        }
        containsKeywordIn(keyword, el.conjunction(), new String[]{"title", "body"});
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all posts in a {@code organization} that a {@code user} can see
     * - If the project is public, then all user including anonymous can find by {@code keyword}
     * - If the project is private, keyword searching is available when
     * the {@code user} is member or admin of the {@code project}.
     * - If the project is protected, keyword searching is available to
     * the project's members and the organization's members
     *
     * @param keyword
     * @param user
     * @param organization
     * @param pageParam
     * @return
     */
    public static Page<Posting> findPosts(String keyword, User user, Organization organization, PageParam pageParam) {
        return postsEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findPosts(String, User, Organization, PageParam)}
     *
     * @param keyword
     * @param user
     * @param organization
     * @return
     */
    public static int countPosts(String keyword, User user, Organization organization) {
        ExpressionList<Posting> el = postsEL(keyword, user, organization);
        return el.findRowCount();
    }

    private static ExpressionList<Posting> postsEL(String keyword, User user, Organization organization) {
        ExpressionList<Posting> el = Posting.finder.where()
                .eq("project.organization", organization);
        Junction<Posting> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInPosting);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInPosting);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    /**
     * Finds all users who contains the {@code keyword} in name or loginId.
     *
     * @param keyword
     * @param pageParam
     * @return
     */
    public static Page<User> findUsers(String keyword, PageParam pageParam) {
        return usersEL(keyword).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findUsers(String, PageParam)}
     *
     * @param keyword
     * @return
     */
    public static int countUsers(String keyword) {
        return usersEL(keyword).findRowCount();
    }

    private static ExpressionList<User> usersEL(String keyword) {
        ExpressionList<User> el = User.find.where()
                .eq("state", UserState.ACTIVE);
        el.disjunction()
            .add(Expr.icontains("name", keyword))
            .add(Expr.icontains("loginId", keyword))
        .endJunction()
        .orderBy().asc("name");
        return el;
    }

    /**
     * Finds all users in a {@code project} who contains the {@code keyword} in name or loginId.
     *
     * @param keyword
     * @param project
     * @param pageParam
     * @return
     */
    public static Page<User> findUsers(String keyword, Project project, PageParam pageParam) {
        return usersEL(keyword, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findUsers(String, Project, PageParam)}
     *
     * @param keyword
     * @param project
     * @return
     */
    public static int countUsers(String keyword, Project project) {
        ExpressionList<User> el = usersEL(keyword, project);
        return el.findRowCount();
    }

    private static ExpressionList<User> usersEL(String keyword, Project project) {
        ExpressionList<User> el = User.find.where()
                .eq("state", UserState.ACTIVE)
                .eq("projectUser.project", project);
        el.disjunction()
            .add(Expr.icontains("name", keyword))
            .add(Expr.icontains("loginId", keyword))
        .endJunction()
        .orderBy().asc("name");
        return el;
    }

    /**
     * Finds all users in an {@code organization} who contains the {@code keyword} in name or loginId.
     *
     * @param keyword
     * @param organization
     * @param pageParam
     * @return
     */
    public static Page<User> findUsers(String keyword, Organization organization, PageParam pageParam) {
        return usersEL(keyword, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findUsers(String, Organization, PageParam)}
     *
     * @param keyword
     * @param organization
     * @return
     */
    public static int countUsers(String keyword, Organization organization) {
        return usersEL(keyword, organization).findRowCount();
    }

    private static ExpressionList<User> usersEL(String keyword, Organization organization) {
        ExpressionList<User> el = User.find.where()
                .eq("state", UserState.ACTIVE)
                .eq("groupUser.organization", organization);
        el.disjunction()
            .add(Expr.icontains("name", keyword))
            .add(Expr.icontains("loginId", keyword))
        .endJunction()
        .orderBy().asc("name");
        return el;
    }

    /**
     * Finds projects that contains {@code keyword} in name or overview.
     *
     * @param keyword
     * @param user
     * @param pageParam
     * @return
     */
    public static Page<Project> findProjects(String keyword, User user, PageParam pageParam) {
        return projectsEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #projectsEL(String, User)}
     *
     * findRowCounts()'s performance is better then findList().size()'s,
     * but findRowCounts() has some issues in some cases:
     * - https://groups.google.com/forum/#!msg/ebean/BRRTpyL_Bek/lK4IcuVhplwJ
     * - https://github.com/ebean-orm/avaje-ebeanorm/issues/165
     * - https://github.com/ebean-orm/avaje-ebeanorm/blob/master/src/test/java/com/avaje/tests/query/other/TestQueryConversationRowCount.java
     *
     * @param keyword
     * @param user
     * @return
     */
    public static int countProjects(String keyword, User user) {
        return projectsEL(keyword, user).findList().size();
    }

    /**
     * Finds all projects in an {@code organization} that contains the {@code keyword} in name or overview.
     *
     * @param keyword
     * @param user
     * @param organization
     * @param pageParam
     * @return
     */
    public static Page<Project> findProjects(String keyword, User user, Organization organization, PageParam pageParam) {
        return projectsEL(keyword, user).eq("organization", organization)
                .findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    /**
     * Returns only the count of the {@link #findProjects(String, User, Organization, PageParam)}
     *
     * @param keyword
     * @param user
     * @param organization
     * @return
     */
    public static int countProjects(String keyword, User user, Organization organization) {
        return projectsEL(keyword, user).eq("organization", organization).findList().size();
    }

    private static ExpressionList<Project> projectsEL(String keyword, User user) {
        ExpressionList<Project> el = Project.find.where();
        if(user.isAnonymous()) {
            el.eq("projectScope", ProjectScope.PUBLIC);
            el.disjunction()
                .icontains("overview", keyword)
                .icontains("name", keyword)
            .endJunction();
        } else {
            Junction<Project> junction = el.conjunction();
            Junction<Project> pj = junction.disjunction();
            pj.add(Expr.eq("projectScope", ProjectScope.PUBLIC)); // public
            List<Organization> orgs = Organization.findOrganizationsByUserLoginId(user.loginId); // protected
            if(!orgs.isEmpty()) {
                pj.and(Expr.in("organization", orgs), Expr.eq("projectScope", ProjectScope.PROTECTED));
            }
            pj.add(Expr.eq("projectUser.user.id", user.id)); // private
            pj.endJunction();
            junction.disjunction()
                .icontains("overview", keyword)
                .icontains("name", keyword)
            .endJunction();
            junction.endJunction();
        }
        el.orderBy().asc("name");
        return el;
    }

    public static Page<Milestone> findMilestones(String keyword, User user, PageParam pageParam) {
        return milestonesEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countMilestones(String keyword, User user) {
        return milestonesEL(keyword, user).findRowCount();
    }

    private static ExpressionList<Milestone> milestonesEL(String keyword, User user) {
        ExpressionList<Milestone> el = Milestone.find.where();
        Junction<Milestone> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInMilestone);
        junction.endJunction();
        el.orderBy().desc("dueDate");
        return el;
    }

    public static Page<Milestone> findMilestones(String keyword, User user, Project project, PageParam pageParam) {
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            return emptyPage();
        }
        return milestonesEL(keyword, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countMilestones(String keyword, User user, Project project) {
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            return 0;
        }
        return milestonesEL(keyword, project).findRowCount();
    }

    private static ExpressionList<Milestone> milestonesEL(String keyword, Project project) {
        ExpressionList<Milestone> el = Milestone.find.where()
                .eq("project", project);
        Junction<Milestone> junction = el.disjunction();
        containsKeywordIn(keyword, junction, new String[]{"title", "contents"});
        junction.endJunction();
        el.orderBy().desc("dueDate");
        return el;
    }

    public static Page<Milestone> findMilestones(String keyword, User user, Organization organization, PageParam pageParam) {
        return milestonesEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countMilestones(String keyword, User user, Organization organization) {
        return milestonesEL(keyword, user, organization).findRowCount();
    }

    private static ExpressionList<Milestone> milestonesEL(String keyword, User user, Organization organization) {
        ExpressionList<Milestone> el = Milestone.find.where()
                .eq("project.organization", organization);
        Junction<Milestone> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, DEFAULT_PATH_TO_PROJECT, containsKeywordInMilestone);
        junction.endJunction();
        el.orderBy().desc("dueDate");
        return el;
    }

    public static Page<IssueComment> findIssueComments(String keyword, User user, PageParam pageParam) {
        return issueCommentsEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countIssueComments(String keyword, User user) {
        return issueCommentsEL(keyword, user).findRowCount();
    }

    private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user) {
        ExpressionList<IssueComment> el = IssueComment.find.where();
        Junction<IssueComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, "issue.project", containsKeywordInIssueComment);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInIssueComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<IssueComment> findIssueComments(String keyword, User user, Project project, PageParam pageParam) {
        return issueCommentsEL(keyword, user, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countIssueComments(String keyword, User user, Project project) {
        return issueCommentsEL(keyword, user, project).findRowCount();
    }

    private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Project project) {
        ExpressionList<IssueComment> el = IssueComment.find.where()
                .eq("issue.project", project);
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            el.eq("authorId", user.id);
        }
        containsKeywordIn(keyword, el.conjunction(), new String[]{"contents"});
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<IssueComment> findIssueComments(String keyword, User user, Organization organization, PageParam pageParam) {
        return issueCommentsEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countIssueComments(String keyword, User user, Organization organization) {
        return issueCommentsEL(keyword, user, organization).findRowCount();
    }

    private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Organization organization) {
        ExpressionList<IssueComment> el = IssueComment.find.where()
                .eq("issue.project.organization", organization);
        Junction<IssueComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, "issue.project", containsKeywordInIssueComment);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInIssueComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<PostingComment> findPostComments(String keyword, User user, PageParam pageParam) {
        return postCommentsEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countPostComments(String keyword, User user) {
        return postCommentsEL(keyword, user).findRowCount();
    }

    private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user) {
        ExpressionList<PostingComment> el = PostingComment.find.where();
        Junction<PostingComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, "posting.project", containsKeywordInPostComment);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInPostComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<PostingComment> findPostComments(String keyword, User user, Project project, PageParam pageParam) {
        return postCommentsEL(keyword, user, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countPostComments(String keyword, User user, Project project) {
        return postCommentsEL(keyword, user, project).findRowCount();
    }

    private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Project project) {
        ExpressionList<PostingComment> el = PostingComment.find.where()
                .eq("posting.project", project);

        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            el.eq("authorId", user.id);
        }
        containsKeywordIn(keyword, el.conjunction(), new String[]{"contents"});
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<PostingComment> findPostComments(String keyword, User user, Organization organization, PageParam pageParam) {
        return postCommentsEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countPostComments(String keyword, User user, Organization organization) {
        return postCommentsEL(keyword, user, organization).findRowCount();
    }

    private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Organization organization) {
        ExpressionList<PostingComment> el = PostingComment.find.where()
                .eq("posting.project.organization", organization);
        Junction<PostingComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, "posting.project", containsKeywordInPostComment);
        equalsUserTemplate(keyword, user, junction, DEFAULT_PATH_TO_AUTHOR, containsKeywordInPostComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<ReviewComment> findReviews(String keyword, User user, PageParam pageParam) {
        return reviewsEL(keyword, user).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countReviews(String keyword, User user) {
        return reviewsEL(keyword, user).findRowCount();
    }

    private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user) {
        ExpressionList<ReviewComment> el = ReviewComment.find.where();
        Junction<ReviewComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, junction, "thread.project", containsKeywordInReviewComment);
        equalsUserTemplate(keyword, user, junction, "author.id", containsKeywordInReviewComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<ReviewComment> findReviews(String keyword, User user, Project project, PageParam pageParam) {
        return reviewsEL(keyword, user, project).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countReviews(String keyword, User user, Project project) {
        return reviewsEL(keyword, user, project).findRowCount();
    }

    private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Project project) {
        ExpressionList<ReviewComment> el = ReviewComment.find.where()
                .eq("thread.project", project);
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            el.eq("author.id", user.id);
        }
        containsKeywordIn(keyword, el.conjunction(), new String[]{"contents"});
        el.orderBy().desc("createdDate");
        return el;
    }

    public static Page<ReviewComment> findReviews(String keyword, User user, Organization organization, PageParam pageParam) {
        return reviewsEL(keyword, user, organization).findPagingList(pageParam.getSize()).getPage(pageParam.getPage());
    }

    public static int countReviews(String keyword, User user, Organization organization) {
        return reviewsEL(keyword, user, organization).findRowCount();
    }

    private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Organization organization) {
        ExpressionList<ReviewComment> el = ReviewComment.find.where()
                .eq("thread.project.organization", organization);
        Junction<ReviewComment> junction = el.disjunction();
        inProjectsTemplate(keyword, user, organization, junction, "thread.project", containsKeywordInReviewComment);
        equalsUserTemplate(keyword, user, junction, "author.id", containsKeywordInReviewComment);
        junction.endJunction();
        el.orderBy().desc("createdDate");
        return el;
    }

    interface JunctionOperation<T> {
        void withJunction(String keyword, Junction<T> junction);
    }

    private static <T> void containsKeywordIn(String keyword, Junction<T> junction, String[] fields) {
        Junction<T> byKeyword = junction.disjunction();
        for(String field : fields) {
            byKeyword.add(Expr.icontains(field, keyword));
        }
        byKeyword.endJunction();
    }

    private static <T> void inProjectsTemplate(String keyword, User user, Organization organization, Junction<T> junction, String pathToProject, JunctionOperation<T> junctionOperation) {
        if(pathToProject == null) {
            pathToProject = "project";
        }

        Junction<T> projectAndKeyword = junction.conjunction();
        if(user.isAnonymous()) {
            projectAndKeyword.eq(pathToProject + ".projectScope", ProjectScope.PUBLIC);
        } else {
            ExpressionList<Project> pel = Project.find.where()
                    .eq("organization", organization)
                    .disjunction()
                    .add(Expr.eq("projectUser.user.id", user.id));
            if(OrganizationUser.exist(organization.id, user.id)) {
                pel.add(Expr.in("projectScope", new ProjectScope[]{ProjectScope.PUBLIC, ProjectScope.PROTECTED}));
            } else {
                pel.add(Expr.eq("projectScope", ProjectScope.PUBLIC));
            }
            pel.endJunction();

            List<Project> projects = pel.findList();
            if(!projects.isEmpty()) {
                projectAndKeyword.in(pathToProject, projects);
            }
        }
        junctionOperation.withJunction(keyword, projectAndKeyword);
        projectAndKeyword.endJunction();
        junction.endJunction();
    }

    private static <T> void inProjectsTemplate(String keyword, User user, Junction<T> junction, String pathToProject, JunctionOperation<T> junctionOperation) {
        if(pathToProject == null) {
            pathToProject = "project";
        }

        Junction<T> projectAndKeyword = junction.conjunction();
        if(user.isAnonymous()) {
            projectAndKeyword.eq(pathToProject + ".projectScope", ProjectScope.PUBLIC);
        } else {
            ExpressionList<Project> pel = Project.find.where().disjunction()
                    .add(Expr.eq("projectScope", ProjectScope.PUBLIC))
                    .add(Expr.eq("projectUser.user.id", user.id));

            List<Organization> orgs = Organization.findOrganizationsByUserLoginId(user.loginId);
            if(!orgs.isEmpty()) {
                pel.and(Expr.in("organization", orgs), Expr.ne("projectScope", ProjectScope.PRIVATE));
            }
            pel.endJunction();

            List<Project> projects = pel.findList();
            if(!projects.isEmpty()) {
                projectAndKeyword.in(pathToProject, projects);
            }
        }
        junctionOperation.withJunction(keyword, projectAndKeyword);
        projectAndKeyword.endJunction();
    }

    private static <T> void equalsUserTemplate(String keyword, User user, Junction<T> junction, String pathToUserId, JunctionOperation<T> junctionOperation) {
        if(!user.isAnonymous()) {
            if(pathToUserId == null) {
                pathToUserId = "authorId";
            }
            Junction<T> userAndKeyword = junction.conjunction();
            userAndKeyword.eq(pathToUserId, user.id);
            junctionOperation.withJunction(keyword, userAndKeyword);
            userAndKeyword.endJunction();
        }
    }

    private static <T> Page<T> emptyPage() {
        return new Page<T>() {
            @Override
            public List<T> getList() {
                return new ArrayList<>();
            }

            @Override
            public int getTotalRowCount() {
                return 0;
            }

            @Override
            public int getTotalPageCount() {
                return 0;
            }

            @Override
            public int getPageIndex() {
                return 0;
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public boolean hasPrev() {
                return false;
            }

            @Override
            public Page<T> next() {
                return null;
            }

            @Override
            public Page<T> prev() {
                return null;
            }

            @Override
            public String getDisplayXtoYofZ(String s, String s2) {
                return null;
            }
        };
    }

}
