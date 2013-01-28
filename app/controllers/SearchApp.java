package controllers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.avaje.ebean.*;
import models.*;
import play.mvc.*;
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
            notFound();
        }
		/* @TODO 쿼리에 대해서 특수문자나 공백 체크 해야함. */
        ContentSearchCondition condition = form(ContentSearchCondition.class).bindFromRequest().get();

        String range = request().getHeader("Range");
        if (range != null) {
            String regex = "pages=(.*)";
            Pattern pattern = Pattern.compile(regex);
            Matcher match = pattern.matcher(range);
            if (match.matches()) {
                int pageNum = Integer.parseInt(match.group(1));
                if (condition.page != 0 && condition.page != pageNum) {
                    play.Logger.warn("Conflict error: condition.page values from query string and from Range header are different.");
                }
                condition.page = pageNum;
            }
        }

        Page<Issue> resultIssues = null;
        Page<Post> resultPosts = null;

        if(!condition.type.equals("post")) {
            resultIssues = Issue.find(project, condition);
        }
        if(!condition.type.equals("issue")) {
            resultPosts = Post.find(project, condition);
        }

        response().setHeader("Accept-Range", "pages");

        if (condition.type.equals("post")) {
            response().setHeader(
                    "Content-Range",
                    "pages " + Integer.toString(resultPosts.getPageIndex() + 1) + "/"
                            + Integer.toString(resultPosts.getTotalPageCount()));
            return status(206, postContentsSearch.render(project, resultPosts));
        } else if (condition.type.equals("issue")) {
            response().setHeader(
                    "Content-Range",
                    "pages " + Integer.toString(resultIssues.getPageIndex() + 1) + "/"
                            + Integer.toString(resultIssues.getTotalPageCount()));
            return status(206, issueContentsSearch.render(project, resultIssues));
        }

        return ok(contentsSearch.render("title.contentSearchResult", project, condition.filter, resultIssues, resultPosts));
    }
}
