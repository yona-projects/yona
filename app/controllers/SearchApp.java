package controllers;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.IsAllowed;
import models.*;
import models.enumeration.Operation;
import models.enumeration.SearchType;
import org.apache.commons.lang3.StringUtils;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.search.result;

import java.util.List;

/**
 * @author Keeun Baik
 */
public class SearchApp extends Controller {

    private static final PageParam DEFAULT_PAGE = new PageParam(0, 20);

    /**
     * Search from all repositories.
     *
     * @return
     */
    public static Result searchAllRepos() {
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        setPage();

        if(StringUtils.isEmpty(keyword) || StringUtils.isEmpty(searchTypeValue)) {
            return badRequest();
        }

        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA) {
            return badRequest();
        }

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

        switch (searchType) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, DEFAULT_PAGE));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, DEFAULT_PAGE));
                break;
            case PROJECT:
                searchResult.setProjects(Search.findProjects(keyword, user, DEFAULT_PAGE));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, DEFAULT_PAGE));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, DEFAULT_PAGE));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, DEFAULT_PAGE));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, DEFAULT_PAGE));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, DEFAULT_PAGE));
                break;
        }
        return ok(result.render("title.search", null, null, searchResult));
    }

    /**
     * Search from an {@link models.Organization}
     *
     * @param organizationName
     * @return
     */
    public static Result searchGroupRepos(String organizationName) {
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        setPage();

        if(StringUtils.isEmpty(organizationName)
                || StringUtils.isEmpty(keyword)
                || StringUtils.isEmpty(searchTypeValue)) {
            return badRequest();
        }

        Organization organization = Organization.findByOrganizationName(organizationName);
        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA) {
            return badRequest();
        }

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

        switch (searchType) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, organization, DEFAULT_PAGE));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, organization, DEFAULT_PAGE));
                break;
            case PROJECT:
                searchResult.setProjects(Search.findProjects(keyword, user, organization, DEFAULT_PAGE));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, organization, DEFAULT_PAGE));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, organization, DEFAULT_PAGE));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, organization, DEFAULT_PAGE));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, organization, DEFAULT_PAGE));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, organization, DEFAULT_PAGE));
                break;
        }

        return ok(result.render("title.search", organization, null, searchResult));
    }

    @IsAllowed(Operation.READ)
    public static Result searchProject(String loginId, String projectName) {
        String searchTypeValue = request().getQueryString("searchType");
        String keyword = request().getQueryString("keyword");
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        setPage();

        if(StringUtils.isEmpty(keyword)
                || StringUtils.isEmpty(searchTypeValue)
                || project == null) {
            return badRequest();
        }

        User user = UserApp.currentUser();
        SearchType searchType = SearchType.getValue(searchTypeValue);

        if(searchType == SearchType.NA || searchType == SearchType.PROJECT) {
            return badRequest();
        }

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

        switch (searchType) {
            case ISSUE:
                searchResult.setIssues(Search.findIssues(keyword, user, project, DEFAULT_PAGE));
                break;
            case USER:
                searchResult.setUsers(Search.findUsers(keyword, project, DEFAULT_PAGE));
                break;
            case POST:
                searchResult.setPosts(Search.findPosts(keyword, user, project, DEFAULT_PAGE));
                break;
            case MILESTONE:
                searchResult.setMilestones(Search.findMilestones(keyword, user, project, DEFAULT_PAGE));
                break;
            case ISSUE_COMMENT:
                searchResult.setIssueComments(Search.findIssueComments(keyword, user, project, DEFAULT_PAGE));
                break;
            case POST_COMMENT:
                searchResult.setPostComments(Search.findPostComments(keyword, user, project, DEFAULT_PAGE));
                break;
            case REVIEW:
                searchResult.setReviews(Search.findReviews(keyword, user, project, DEFAULT_PAGE));
                break;
        }

        return ok(result.render("title.search", null, project, searchResult));
    }

    private static void setPage() {
        String pageNumString = request().getQueryString("pageNum");

        if(pageNumString != null) {
            int pageNum = Integer.parseInt(pageNumString);
            DEFAULT_PAGE.setPage(pageNum - 1);
        }
    }

}
