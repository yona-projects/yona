package controllers;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import models.Project;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;

import play.mvc.Controller;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.RepositoryService;
import utils.RequestUtil;
import views.html.code.history;
import views.html.code.diff;

public class CodeHistoryApp extends Controller {

    private static final int HISTORY_ITEM_LIMIT = 25;

    public static Result history(String userName, String projectName) throws IOException,
            UnsupportedOperationException, ServletException, NoHeadException, GitAPIException,
            SVNException {
        Project project = Project.findByName(projectName);
        String url = CodeApp.getURL(userName, projectName);
        String pageStr = RequestUtil.getFirstValueFromQuery(request().queryString(), "page");
        int page = 0;
        if (pageStr != null) {
            page = Integer.parseInt(pageStr);
        }

        List<Commit> commits = RepositoryService.getRepository(project).getHistory(page,
                HISTORY_ITEM_LIMIT);

        return ok(history.render(url, project, commits, page));
    }

    public static Result show(String userName, String projectName, String commitId)
            throws IOException, UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByName(projectName);
        String patch = RepositoryService.getRepository(project).getPatch(commitId);

        return ok(diff.render(project, patch));
    }

}