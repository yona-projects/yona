package controllers;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import models.Project;

import play.mvc.Controller;
import play.mvc.Result;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import views.html.code.gitHistory;
import views.html.code.gitDiff;

public class CommitsApp extends Controller {

    public static Result history(String userName, String projectName) throws IOException,
            UnsupportedOperationException, ServletException, NoHeadException, GitAPIException {
        String[] pages = request().queryString().get("page");
        int page = 0;
        if (pages != null && pages.length > 0) {
            page = Integer.parseInt(pages[0]);
        }

        Project project = Project.findByName(projectName);
        PlayRepository repo = RepositoryService.getRepository(project);
        List<RevCommit> history = repo.getHistory(page, 25);
        history.get(0).getParentCount();
        String url = CodeApp.getURL(userName, projectName);

        return ok(gitHistory.render(url, project, history, page));
    }

    public static Result show(String userName, String projectName, String commitId)
            throws IOException, UnsupportedOperationException, ServletException, GitAPIException {
        Project project = Project.findByName(projectName);
        PlayRepository repo = RepositoryService.getRepository(project);
        String patch = repo.getPatch(commitId);
        return ok(gitDiff.render(project, patch));
    }

}
