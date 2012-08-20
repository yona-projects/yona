package controllers;

import git.GitRepository;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tigris.subversion.javahl.ClientException;

import play.mvc.Controller;
import play.mvc.Result;
import svn.SVNRepository;

public class CodeApp extends Controller {
	private static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    public static Result view(String userName, String projectName, String path) throws IOException {
        String vcs = ProjectApp.getProject(userName, projectName).vcs;
        if (VCS_GIT.equals(vcs)) {
            return GitApp.viewCode(userName, projectName, path);
        } else {
            return status(501, vcs + " is not supported!");
        }
    }
    
    public static Result ajaxRequest(String userName, String projectName, String path) throws IOException, NoHeadException, GitAPIException {
        try {
            return ok(new GitRepository(userName, projectName).findFileInfo(path));
        } catch (NoHeadException e) {
            return forbidden();
        }
    }
    
    public static void createRepository(String userName, String projectName, String type) throws IOException, ClientException {
        if (type.equals(VCS_GIT)) {
            GitRepository.create(userName, projectName);
        } else if (type.equals(VCS_SUBVERSION)) {
            SVNRepository.create(userName, projectName);
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
}
