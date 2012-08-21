package controllers;

import com.avaje.ebean.Page;
import models.Issue;
import models.Post;
import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.search.contentsSearch;
import views.html.search.issueContentsSearch;
import views.html.search.postContentsSearch;

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

    public static Result contentsSearch(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);

        if (project == null) {
            notFound();
        }

        ContentSearchCondition condition = form(ContentSearchCondition.class).bindFromRequest().get();

        Page<Issue> resultIssues = null;
        Page<Post> resultPosts = null;

        if(!condition.type.equals("post")) {
            resultIssues = Issue.findIssues(project, condition);
        }
        if(!condition.type.equals("issue")) {
            resultPosts = Post.findPosts(project, condition);
        }

        if(condition.type.equals("post")) {
            return ok(postContentsSearch.render(project, resultPosts));
        } else if(condition.type.equals("issue")) {
            return ok(issueContentsSearch.render(project, resultIssues));
        }

        return ok(contentsSearch.render("title.contentSearchResult", project, condition.filter, resultIssues, resultPosts));
    }
}