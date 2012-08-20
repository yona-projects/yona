package controllers;

import com.avaje.ebean.Page;
import models.Issue;
import models.Post;
import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.search.contentsSearch;

public class SearchApp extends Controller {

	public static class ContentSearchCondition {

		public String filter;
		public int page;
		public int pageSize;

		public ContentSearchCondition() {
			this.filter = "";
			this.page = 1;
			this.pageSize = 10;
		}
	}

	public static Result contentsSearch(String userName, String projectName) {
		Project project = ProjectApp.getProject(userName, projectName);

		if (project == null) {
			notFound();
		}

		ContentSearchCondition condition = form(ContentSearchCondition.class).bindFromRequest().get();

		Page<Issue> resultIssues = Issue.findIssues(project, condition);
		Page<Post> resultPosts = Post.findPosts(project, condition);


		return ok(contentsSearch.render("title.contentSearchResult", project, condition.filter, resultIssues, resultPosts));
	}
}