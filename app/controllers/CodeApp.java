package controllers;

import git.GitRepository;

import java.io.*;

import models.Project;

import org.eclipse.jgit.api.errors.*;
import org.tigris.subversion.javahl.ClientException;

import play.mvc.*;

public class CodeApp extends Controller {

    public static Result view(String ownerName, String projectName, String path) throws IOException {
        //FIXME use ownerName
        String vcs = Project.findByName(projectName).vcs;
        if (vcs.equals("GIT")) {
            return GitApp.viewCode(ownerName, projectName, path);
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
        if (type.equals("GIT")) {
            GitRepository.createRepository(ownerName, projectName);
        } else if (type.equals("Subversion")) {
            String svnPath = new File(SvnApp.REPO_PREFIX + projectName).getAbsolutePath();
            new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
}
