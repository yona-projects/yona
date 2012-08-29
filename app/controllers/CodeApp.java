package controllers;

import java.io.IOException;

import models.Project;
import play.mvc.*;
import views.html.code.gitView;

public class CodeApp extends Controller {
	public static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    public static Result codeBrowser(String userName, String projectName) throws IOException {
        String vcs = ProjectApp.getProject(userName, projectName).vcs;
        if (VCS_GIT.equals(vcs)) {
            return ok(gitView.render(GitApp.getURL(userName, projectName),
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
        if (VCS_GIT.equals(vcs)) {
            return GitApp.showRawCode(userName, projectName, path);
        } else if (vcs.equals(VCS_SUBVERSION)) {
            return SvnApp.showRawCode(userName, projectName, path);
        }
        return TODO;
    }
    
    public static void createRepository(String userName, String projectName, String type) throws Exception {
        if (type.equals(VCS_GIT)) {
            GitApp.createRepository(userName, projectName);
        } else if (type.equals(VCS_SUBVERSION)) {
            SvnApp.createRepository(userName, projectName);
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
}
