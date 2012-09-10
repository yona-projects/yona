package playRepository;

import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;

public class RepositoryFactory {
    public static PlayRepository getRepository(Project project) throws IOException,
            ServletException, UnsupportedOperationException {
        if (project.vcs.equals(RepositoryService.VCS_GIT)) {
            return new GitRepository(project.owner, project.name);
        } else if (project.vcs.equals(RepositoryService.VCS_SUBVERSION)) {
            return new SVNRepository(project.owner, project.name);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
