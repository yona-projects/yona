package Repository;

import java.io.IOException;

import javax.servlet.ServletException;

import controllers.CodeApp;
import models.Project;

public class RepositoryFactory {
    public static Repo getRepository(Project project) throws Exception{
        if(project.vcs == CodeApp.VCS_GIT) { 
            return new GitRepository(project.owner, project.name);
        } else if(project.vcs == CodeApp.VCS_SUBVERSION) {
            return new SVNRepository(project.owner, project.name);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
