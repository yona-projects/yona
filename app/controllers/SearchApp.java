package controllers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.avaje.ebean.*;
import models.*;
import play.mvc.*;

import utils.Views;
import views.html.search.*;

import static play.data.Form.form;

public class SearchApp extends Controller {

    public static class ContentSearchCondition {
        public String filter;
        public int page;
        public int pageSize;
        public String type;

        public ContentSearchCondition() {
            this.filter = "";
            this.page = 1;
            this.pageSize = 10;
            this.type = "all";
        }
    }

    public static Result contentsSearch(String userName, String projectName, int page) {
        Project project = ProjectApp.getProject(userName, projectName);

        if (project == null) {
            notFound(Views.NotFound.render("error.notfound"));
        }
		/* @TODO 쿼리에 대해서 특수문자나 공백 체크 해야함. */
        ContentSearchCondition condition = form(ContentSearchCondition.class).bindFromRequest().get();

        // Get pageNum from Range request header.
        String range = request().getHeader("Range");
        if (range != null) {
            String regex = "pages\\s*=\\s*(.*)";
            Pattern pattern = Pattern.compile(regex);
            Matcher match = pattern.matcher(range);
            if (match.matches()) {
                int pageNum = Integer.parseInt(match.group(1).trim());
                if (condition.page != 0 && condition.page != pageNum) {
                    play.Logger.warn("Conflict error: condition.page values from query string and from Range header differ from each other.");
                }
                condition.page = pageNum;
            }
        }

        Page<Issue> resultIssues = null;
        Page<Posting> resultPosts = null;

        if(!condition.type.equals("post")) {
            resultIssues = AbstractPosting.find(Issue.finder, project, condition);
        }
        if(!condition.type.equals("issue")) {
            resultPosts = AbstractPosting.find(Posting.finder, project, condition);
        }

        response().setHeader("Accept-Ranges", "pages");

        if (condition.type.equals("post")) {
            response().setHeader(
                    "Content-Range",
                    "pages " + Integer.toString(resultPosts.getPageIndex() + 1) + "/"
                            + Integer.toString(resultPosts.getTotalPageCount()));
            return status(Http.Status.PARTIAL_CONTENT, postContentsSearch.render(project, resultPosts));
        } else if (condition.type.equals("issue")) {
            response().setHeader(
                    "Content-Range",
                    "pages " + Integer.toString(resultIssues.getPageIndex() + 1) + "/"
                            + Integer.toString(resultIssues.getTotalPageCount()));
            return status(Http.Status.PARTIAL_CONTENT, issueContentsSearch.render(project, resultIssues));
        }

        return ok(contentsSearch.render("title.contentSearchResult", project, condition.filter, resultIssues, resultPosts));
    }
}
