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
package controllers;

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.*;
import models.enumeration.Operation;
import models.enumeration.SearchType;
import org.apache.commons.lang3.StringUtils;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ErrorViews;
import views.html.search.*;

@AnonymousCheck
public class SearchApp extends Controller {

    private static final PageParam DEFAULT_PAGE = new PageParam(0, 20);

    /**
     * Search contents that current user can read in all projects.
     *
     * @return
     */
    public static Result searchInAll() {
        // SearchCondition from param
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        PageParam pageParam = getPage();

        if(StringUtils.isEmpty(keyword) || StringUtils.isEmpty(searchTypeValue)) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        SearchResult searchResult = getSearchResult(keyword, user, searchType);
        switch (searchResult.getSearchType()) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, pageParam));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, pageParam));
                break;
            case PROJECT:
                searchResult.setProjects(Search.findProjects(keyword, user, pageParam));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, pageParam));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, pageParam));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, pageParam));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, pageParam));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, pageParam));
                break;
        }
        return ok(result.render("title.search", null, null, searchResult));
    }

    private static SearchResult getSearchResult(String keyword, User user, SearchType searchType) {
        SearchResult searchResult = new SearchResult();
        searchResult.setKeyword(keyword);
        searchResult.setSearchType(searchType);
        searchResult.setProjectsCount(Search.countProjects(keyword, user));
        searchResult.setUsersCount(Search.countUsers(keyword));
        searchResult.setIssuesCount(Search.countIssues(keyword, user));
        searchResult.setPostsCount(Search.countPosts(keyword, user));
        searchResult.setMilestonesCount(Search.countMilestones(keyword, user));
        searchResult.setIssueCommentsCount(Search.countIssueComments(keyword, user));
        searchResult.setPostCommentsCount(Search.countPostComments(keyword, user));
        searchResult.setReviewsCount(Search.countReviews(keyword, user));
        searchResult.updateSearchType();
        return searchResult;
    }

    /**
     * Search contents that current user can read in a group.
     *
     * @param organizationName
     * @return
     */
    public static Result searchInAGroup(String organizationName) {
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        PageParam pageParam = getPage();

        if(StringUtils.isEmpty(organizationName)
                || StringUtils.isEmpty(keyword)
                || StringUtils.isEmpty(searchTypeValue)) {
            return badRequest();
        }

        Organization organization = Organization.findByName(organizationName);
        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA || organization == null) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        SearchResult searchResult = getSearchResult(keyword, user, organization, searchType);

        switch (searchResult.getSearchType()) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, organization, pageParam));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, organization, pageParam));
                break;
            case PROJECT:
                searchResult.setProjects(Search.findProjects(keyword, user, organization, pageParam));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, organization, pageParam));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, organization, pageParam));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, organization, pageParam));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, organization, pageParam));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, organization, pageParam));
                break;            
        }

        return ok(result.render("title.search", organization, null, searchResult));
    }

    private static SearchResult getSearchResult(String keyword, User user, Organization organization, SearchType searchType) {
        SearchResult searchResult = new SearchResult();
        searchResult.setSearchType(searchType);
        searchResult.setKeyword(keyword);
        searchResult.setProjectsCount(Search.countProjects(keyword, user, organization));
        searchResult.setUsersCount(Search.countUsers(keyword, organization));
        searchResult.setIssuesCount(Search.countIssues(keyword, user, organization));
        searchResult.setPostsCount(Search.countPosts(keyword, user, organization));
        searchResult.setMilestonesCount(Search.countMilestones(keyword, user, organization));
        searchResult.setIssueCommentsCount(Search.countIssueComments(keyword, user, organization));
        searchResult.setPostCommentsCount(Search.countPostComments(keyword, user, organization));
        searchResult.setReviewsCount(Search.countReviews(keyword, user, organization));
        searchResult.updateSearchType();
        return searchResult;
    }

    /**
     * Search contents that current user can read in a project.
     *
     * @param loginId
     * @param projectName
     * @return
     */
    @IsAllowed(Operation.READ)
    public static Result searchInAProject(String loginId, String projectName) {
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        PageParam pageParam = getPage();

        if(StringUtils.isEmpty(keyword)
                || StringUtils.isEmpty(searchTypeValue)
                || project == null) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA || searchType == SearchType.PROJECT) {
            return badRequest(ErrorViews.BadRequest.render());
        }

        SearchResult searchResult = getSearchResult(keyword, user, project, searchType);

        switch (searchResult.getSearchType()) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, project, pageParam));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, project, pageParam));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, project, pageParam));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, project, pageParam));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, project, pageParam));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, project, pageParam));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, project, pageParam));
                break;
        }

        return ok(result.render("title.search", null, project, searchResult));
    }

    private static SearchResult getSearchResult(String keyword, User user, Project project, SearchType searchType) {
        SearchResult searchResult = new SearchResult();
        searchResult.setSearchType(searchType);
        searchResult.setKeyword(keyword);
        searchResult.setUsersCount(Search.countUsers(keyword, project));
        searchResult.setIssuesCount(Search.countIssues(keyword, user, project));
        searchResult.setPostsCount(Search.countPosts(keyword, user, project));
        searchResult.setMilestonesCount(Search.countMilestones(keyword, user, project));
        searchResult.setIssueCommentsCount(Search.countIssueComments(keyword, user, project));
        searchResult.setPostCommentsCount(Search.countPostComments(keyword, user, project));
        searchResult.setReviewsCount(Search.countReviews(keyword, user, project));
        searchResult.updateSearchType();
        return searchResult;
    }

    private static PageParam getPage() {
        PageParam pageParam = new PageParam(DEFAULT_PAGE.getPage(), DEFAULT_PAGE.getSize());
        String pageNumString = request().getQueryString("pageNum");
        if(pageNumString != null) {
            int pageNum = Integer.parseInt(pageNumString);
            pageParam.setPage(pageNum - 1);
        }
        return pageParam;
    }

}
