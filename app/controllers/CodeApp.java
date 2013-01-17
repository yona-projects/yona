package controllers;

import models.Project;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.tmatesoft.svn.core.SVNException;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.RepositoryService;
import utils.Config;
import play.Logger;
import views.html.code.codeView;
import views.html.code.nohead;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CodeApp extends Controller {
    //TODO 리팩토링이 시급합니다.
	public static Result codeBrowser(String userName, String projectName) throws IOException, UnsupportedOperationException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(501, project.vcs + " is not supported!");
        }

        List<String> branches = RepositoryService.getRepository(project).getBranches();

        if (RepositoryService.VCS_GIT.equals(project.vcs) && branches.size() == 0) {
            return ok(nohead.render(project));
        }

        return ok(codeView.render(project, branches));
    }
	public static Result codeBrowserWithBranch(String userName, String projectName, String branch) throws IOException, UnsupportedOperationException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(501, project.vcs + " is not supported!");
        }

        List<String> branches = RepositoryService.getRepository(project).getBranches();

        return ok(codeView.render(project, branches));
    }

    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception{
        ObjectNode findFileInfo = RepositoryService.getMetaDataFrom(userName, projectName, path);
        if(findFileInfo != null) {
            return ok(findFileInfo);
        } else {
            return status(403);
        }
    }
    public static Result ajaxRequestWithBranch(String userName, String projectName, String branch, String path) throws AmbiguousObjectException, NoHeadException, UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException{
        ObjectNode findFileInfo = RepositoryService.getMetaDataFrom(userName, projectName, path, branch);
        if(findFileInfo != null) {
            return ok(findFileInfo);
        } else {
            return status(403);
        }
    }

    public static Result showRawFile(String userName, String projectName, String path) throws Exception{
        return ok(RepositoryService.getFileAsRaw(userName, projectName, path));
    }

    public static String getURL(String ownerName, String projectName) {
        Project project = ProjectApp.getProject(ownerName, projectName);

        if (RepositoryService.VCS_GIT.equals(project.vcs)) {
            return utils.Url.create(Arrays.asList(ownerName, projectName), request().host());
        } else if (RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return utils.Url.create(Arrays.asList("svn", ownerName, projectName), request().host());
        } else {
            return null;
        }
    }
}
