package repository;

import models.Project;
import controllers.CodeApp;

public class RepositoryFactory {
    public static Repo getRepository(Project project) throws Exception{
        if(project.vcs.equals(CodeApp.VCS_GIT)) { 
            return new GitRepository(project.owner, project.name);
        } else if(project.vcs.equals(CodeApp.VCS_SUBVERSION)) {
            return new SVNRepository(project.owner, project.name);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
