package controllers;

import java.io.IOException;

import models.Project;
import play.mvc.*;
import playRepository.RepositoryService;
import views.html.code.gitView;
import views.html.code.svnView;

public class CodeApp extends Controller {
	public static Result codeBrowser(String userName, String projectName) throws IOException {
        String vcs = ProjectApp.getProject(userName, projectName).vcs;
        if (RepositoryService.VCS_GIT.equals(vcs)) {
            return ok(gitView.render(GitApp.getURL(userName, projectName),
                    Project.findByName(projectName)));
        } else if (RepositoryService.VCS_SUBVERSION.equals(vcs)) {
            return ok(svnView.render(SvnApp.getURL(userName, projectName),
                    Project.findByName(projectName)));
        } else {
            return status(501, vcs + " is not supported!");
        }
    }
    
    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception{
        //TODO Svn과 Git의 분리필요.
        return GitApp.ajaxRequest(userName, projectName, path);
    }
    
    public static Result showRawFile(String userName, String projectName, String path) throws Exception{
        String vcs = ProjectApp.getProject(userName, projectName).vcs;
        if (RepositoryService.VCS_GIT.equals(vcs)) {
            return GitApp.showRawCode(userName, projectName, path);
        } else if (vcs.equals(RepositoryService.VCS_SUBVERSION)) {
            return SvnApp.showRawCode(userName, projectName, path);
        }
        return TODO;
    }
}
