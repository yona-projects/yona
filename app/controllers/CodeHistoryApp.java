package controllers;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import models.Project;
import models.enumeration.Operation;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;

import play.mvc.Controller;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.HttpUtil;
import views.html.code.history;
import views.html.code.nohead;
import views.html.code.diff;

public class CodeHistoryApp extends Controller {

    private static final int HISTORY_ITEM_LIMIT = 25;

    public static Result historyUntilHead(String userName, String projectName) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        return history(userName, projectName, null);
    }

    public static Result history(String loginId, String projectName, String branch) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        PlayRepository repository = RepositoryService.getRepository(project);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        String pageStr = HttpUtil.getFirstValueFromQuery(request().queryString(), "page");
        int page = 0;
        if (pageStr != null) {
            page = Integer.parseInt(pageStr);
        }

        try {
            List<Commit> commits = repository.getHistory(page, HISTORY_ITEM_LIMIT, branch);
            return ok(history.render(project, commits, page, branch));
        } catch (NoHeadException e) {
            return ok(nohead.render(project));
        }
    }

    public static Result show(String loginId, String projectName, String commitId)
            throws IOException, UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }
        String patch = RepositoryService.getRepository(project).getPatch(commitId);

        return ok(diff.render(project, commitId, patch));
    }

}
