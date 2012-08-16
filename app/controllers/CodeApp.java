package controllers;

import git.GitRepository;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tigris.subversion.javahl.ClientException;

import play.mvc.Controller;
import play.mvc.Result;

public class CodeApp extends Controller {
	public static final String VCS_GIT = "GIT";

    public static Result view(String userName, String projectName, String path) throws IOException {
        String vcs = ProjectApp.getProject(userName, projectName).vcs;
        if (VCS_GIT.equals(vcs)) {
            return GitApp.viewCode(userName, projectName, path);
        } else {
            return status(501, vcs + " is not supported!");
        }
    }
    
    public static Result ajaxRequest(String ownerName, String projectName, String path) throws IOException, NoHeadException, GitAPIException {
        try {
            return ok(new GitRepository(ownerName, projectName).findFileInfo(path));
        } catch (NoHeadException e) {
            return forbidden();
        }
    }
    
    public static void createRepository(String ownerName, String projectName, String type) throws IOException, ClientException {
        if (type.equals(VCS_GIT)) {
            GitRepository.createRepository(ownerName, projectName);
        } else if (type.equals("Subversion")) {
            String svnPath = new File(SvnApp.REPO_PREFIX + projectName).getAbsolutePath();
            new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
}
