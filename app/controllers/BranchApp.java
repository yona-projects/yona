package controllers;

import controllers.annotation.ProjectAccess;
import models.Project;
import models.enumeration.Operation;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.GitBranch;
import playRepository.GitRepository;
import views.html.code.branches;

import java.io.IOException;
import java.util.List;

/**
 * @author Keesun Baik
 */
public class BranchApp extends Controller {

    @ProjectAccess(value = Operation.READ, isGitOnly = true)
    public static Result branches(String loginId, String projectName) throws IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        List<GitBranch> allBranches = new GitRepository(project).getAllBranches();
        return ok(branches.render(project, allBranches));
    }

    @ProjectAccess(value = Operation.DELETE, isGitOnly = true)
    public static Result deleteBranch(String loginId, String projectName, String branchName) throws IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        Repository repository = GitRepository.buildGitRepository(project);
        GitRepository.deleteBranch(repository, branchName);
        return redirect(routes.BranchApp.branches(loginId, projectName));
    }

}
